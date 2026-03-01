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

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.firstapp.messagesms.database.AppDatabase;
import com.firstapp.messagesms.database.ScheduledMessage;

import java.util.List;

public class ScheduledMessagesActivity extends AppCompatActivity {

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_messages);

        db = Room.databaseBuilder(getApplicationContext(),
                        AppDatabase.class, "sms_scheduler_db")
                .allowMainThreadQueries()
                .build();

        RecyclerView recyclerView = findViewById(R.id.recyclerScheduledMessages);

        List<ScheduledMessage> messages = db.scheduledMessageDao().getAllMessages();

        ScheduledMessageAdapter adapter =
                new ScheduledMessageAdapter(messages, db);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}