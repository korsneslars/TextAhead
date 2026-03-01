/*
 * TextAhead - Schedule SMS messages for later delivery.
 * Copyright (C) 2026 Lars Korsnes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.firstapp.messagesms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;

import com.firstapp.messagesms.database.AppDatabase;
import com.firstapp.messagesms.database.ScheduledMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.app.Activity;

public class ScheduledMessageAdapter extends RecyclerView.Adapter<ScheduledMessageAdapter.ViewHolder> {

    private List<ScheduledMessage> messages;
    private AppDatabase db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);

        if (holder.countdownRunnable != null) {
            handler.removeCallbacks(holder.countdownRunnable);
        }
    }

    public ScheduledMessageAdapter(List<ScheduledMessage> messages, AppDatabase db) {
        this.messages = messages;
        this.db = db;


    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scheduled_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ScheduledMessage msg = messages.get(position);

        // 1️⃣ Contact name (or fallback to phone)
        String displayName;
        if (msg.contactName != null && !msg.contactName.isEmpty()) {
            displayName = msg.contactName;
        } else {
            displayName = getContactName(holder.itemView.getContext(), msg.phone);
            if (displayName == null || displayName.isEmpty()) {
                displayName = msg.phone; // fallback if not in contacts
            }
        }
        holder.textContact.setText(displayName);

        // 2️⃣ Message
        holder.textMessage.setText(msg.message);

        // 3️⃣ Formatted scheduled date/time
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
        String formattedDate = sdf.format(new java.util.Date(msg.scheduledTimeMillis));
        holder.textTime.setText(formattedDate);

        // 4️⃣ Stop previous countdown if any
        if (holder.countdownRunnable != null) {
            handler.removeCallbacks(holder.countdownRunnable);
        }

        // 5️⃣ Countdown runnable
        long scheduledTime = msg.scheduledTimeMillis;
        holder.countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long diff = scheduledTime - System.currentTimeMillis();

                if (diff <= 0) {
                    holder.countdown.setText("Sending...");
                    return;
                }

                long seconds = diff / 1000;

                long days = seconds / 86400;
                seconds %= 86400;

                long hours = seconds / 3600;
                seconds %= 3600;

                long minutes = seconds / 60;
                seconds %= 60;

                List<String> parts = new ArrayList<>();

                if (days > 0) parts.add(days + " day" + (days > 1 ? "s" : ""));
                if (hours > 0) parts.add(hours + " hour" + (hours > 1 ? "s" : ""));
                if (minutes > 0) parts.add(minutes + " minute" + (minutes > 1 ? "s" : ""));
                if (seconds > 0 || parts.isEmpty()) parts.add(seconds + " second" + (seconds != 1 ? "s" : ""));

                String text;
                if (parts.size() == 1) {
                    text = parts.get(0);
                } else {
                    // Join with commas, add 'and' before last unit
                    text = String.join(", ", parts.subList(0, parts.size() - 1))
                            + " and " + parts.get(parts.size() - 1);
                }

                text = "In " + text;

                holder.countdown.setText(text);

                handler.postDelayed(this, 1000);
            }
        };

        handler.post(holder.countdownRunnable);

        // 6️⃣ Delete button click listener
        holder.btnDelete.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            ScheduledMessage toDelete = messages.get(currentPosition);

            // Cancel WorkManager job if exists
            if (toDelete.workRequestId != null) {
                WorkManager.getInstance(v.getContext())
                        .cancelWorkById(UUID.fromString(toDelete.workRequestId));
            }

            // Delete from database off main thread
            new Thread(() -> db.scheduledMessageDao().delete(toDelete)).start();

            // Remove from list and notify adapter
            messages.remove(currentPosition);
            notifyItemRemoved(currentPosition);

            // ⚡ Check if list is now empty
            if (messages.isEmpty()) {
                // Post back to main thread
                holder.itemView.post(() -> {
                    Context context = holder.itemView.getContext();
                    if (context instanceof Activity) {
                        ((Activity) context).finish(); // closes ScheduledMessagesActivity
                    }
                });
            }


        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textContact, textMessage, textTime;
        Button btnDelete;
        TextView countdown;
        Runnable countdownRunnable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textContact = itemView.findViewById(R.id.textContact);
            textMessage = itemView.findViewById(R.id.textMessage);
            textTime = itemView.findViewById(R.id.textTime);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            countdown = itemView.findViewById(R.id.textCountdown);
        }
    }

    private String getContactName(Context context, String phoneNumber) {

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );

        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                null,
                null,
                null
        );

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
            cursor.close();
        }

        return null;
    }


}
