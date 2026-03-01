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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.firstapp.messagesms.database.AppDatabase;
import com.firstapp.messagesms.database.ScheduledMessage;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> contactPickerLauncher;
    EditText editTextPhone,editTextMessage;
    private Button btnSend, btnViewScheduled, btnPickDateTime;
    private TextView textSelectedDateTime;

    private int selectedYear, selectedMonth, selectedDay;
    private int selectedHour, selectedMinute;
    private static final int REQUEST_SMS_PERMISSION = 101;
    private static final int REQUEST_CONTACT_PERMISSION = 102;

    private AppDatabase db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextMessage = findViewById(R.id.editTextMessage);
        editTextPhone = findViewById(R.id.editTextPhone);

        btnSend = findViewById(R.id.btnSend);
        btnPickDateTime = findViewById(R.id.btnPickDateTime);
        textSelectedDateTime = findViewById(R.id.textScheduled); // optional label
        btnViewScheduled = findViewById(R.id.btnViewScheduled);

        btnSend.setEnabled(false); // initially disabled

        // Attach to both fields
        editTextPhone.addTextChangedListener(inputWatcher);
        editTextMessage.addTextChangedListener(inputWatcher);

        db = AppDatabase.getInstance(this);

        btnSend.setAlpha(0.1f);

        btnSend.setOnClickListener(v -> {

            // Get user input
            String phone = editTextPhone.getText().toString().trim();
            String message = editTextMessage.getText().toString().trim();

            // Validate phone number
            if (phone.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.telefonnummer, Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate message
            if (message.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.meddealande, Toast.LENGTH_SHORT).show();
                return;
            }

            // Check SMS permission
            if (checkSelfPermission(Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQUEST_SMS_PERMISSION);
                return; // Wait for permission callback
            }

            // Decide whether to send immediately or schedule
            if (selectedYear == 0) {
                // No date/time selected → send immediately
                sendSmsNow(phone, message);
            } else {
                // Date/time selected → schedule SMS
                Calendar calendar = Calendar.getInstance();
                calendar.set(selectedYear, selectedMonth, selectedDay,
                        selectedHour, selectedMinute, 0);

                long scheduledTimeMillis = calendar.getTimeInMillis();
                long currentTime = System.currentTimeMillis();

                if (scheduledTimeMillis < currentTime) {
                    Toast.makeText(MainActivity.this,
                            "Selected time is in the past!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                scheduleSms(phone, message);
            }
        });



        Button btnPickContact = findViewById(R.id.btnPickContact);

        btnPickContact.setOnClickListener(v -> {
            // Check if we already have permission to read contacts
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Request it if not granted
                requestPermissions(
                        new String[]{Manifest.permission.READ_CONTACTS},
                        REQUEST_CONTACT_PERMISSION
                );
            } else {
                // Permission already granted → open contact picker
                openContactPicker();
            }
        });





        contactPickerLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK
                                    && result.getData() != null) {

                                Uri contactUri = result.getData().getData();
                                fetchPhoneNumber(contactUri);
                            }
                        });

        btnPickDateTime.setOnClickListener(v -> {

            Calendar calendar = Calendar.getInstance();

            // Date Picker
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    MainActivity.this,
                    (view, year, month, dayOfMonth) -> {

                        // Store selected date
                        selectedYear = year;
                        selectedMonth = month;
                        selectedDay = dayOfMonth;

                        // ⚡ Hide keyboard when started picking
                        hideKeyboard();

                        // After selecting date → open time picker
                        TimePickerDialog timePickerDialog = new TimePickerDialog(
                                MainActivity.this,
                                (timeView, hourOfDay, minute) -> {

                                    selectedHour = hourOfDay;
                                    selectedMinute = minute;

                                    // Update label
                                    Calendar selected = Calendar.getInstance();
                                    selected.set(year, month, dayOfMonth, hourOfDay, minute);
                                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy HH:mm", Locale.getDefault());
                                    textSelectedDateTime.setText(sdf.format(selected.getTime()));
                                    // ⚡ Hide keyboard when started picking
                                    hideKeyboard();

                                    // Optionally update send button text
                                    updateScheduledText();


                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                        );

                        timePickerDialog.show();

                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );

            datePickerDialog.show();
        });

        btnViewScheduled.setOnClickListener(v -> {
            new Thread(() -> {
                int count = db.scheduledMessageDao().getPendingCount();

                runOnUiThread(() -> {
                    if (count == 0) {
                        Toast.makeText(this, "No scheduled messages", Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(this, ScheduledMessagesActivity.class));
                    }
                });
            }).start();
        });

    }  // onCreate

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_SMS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("Main Activity", "onRequestPermissionsResult: SMS okay");
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CONTACT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open contact picker
                openContactPicker();
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendSmsNow(String phone, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
            resetForm();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS", Toast.LENGTH_SHORT).show();
            resetForm();
        }
    }

    private void scheduleSms(String phone, String message) {

        Calendar calendar = Calendar.getInstance();
        calendar.set(selectedYear, selectedMonth, selectedDay,
                selectedHour, selectedMinute, 0);

        long scheduledTimeMillis = calendar.getTimeInMillis();
        long delay = scheduledTimeMillis - System.currentTimeMillis();

        Data data = new Data.Builder()
                .putString("phone", phone)
                .putString("message", message)
                .build();

        OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(SmsWorker.class)
                        .setInputData(data)
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(this).enqueue(workRequest);

        resetForm();

        // ---- SAVE TO ROOM ----
        ScheduledMessage scheduledMessage = new ScheduledMessage();
        scheduledMessage.phone = phone;
        scheduledMessage.contactName = null; // or your contact name variable
        scheduledMessage.message = message;
        scheduledMessage.scheduledTimeMillis = scheduledTimeMillis;
        scheduledMessage.workRequestId = workRequest.getId().toString();

        new Thread(() -> db.scheduledMessageDao().insert(scheduledMessage)).start();

        Toast.makeText(this, "Message scheduled", Toast.LENGTH_SHORT).show();
    }


    private void openContactPicker() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);

        contactPickerLauncher.launch(intent);
    }

    private void fetchPhoneNumber(Uri contactUri) {

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(
                contactUri,
                projection,
                null,
                null,
                null)) {

            if (cursor != null && cursor.moveToFirst()) {

                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);

                String number = cursor.getString(numberIndex);

                editTextPhone.setText(number);
            }
        }
    }

    private void updateScheduledText() {

        if (selectedYear != 0) { // User picked a date/time
            String dateTime = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear
                    + " " + selectedHour + ":" + String.format(Locale.UK, "%02d", selectedMinute);

            String formattedText = getString(R.string.sceduled_for, dateTime);
            textSelectedDateTime.setText(formattedText);
            btnSend.setText(R.string.scheduled_send); // update button text
        } else {
            textSelectedDateTime.setText(R.string.send_immediatly);
            btnSend.setText(R.string.send);
        }
    }

    TextWatcher inputWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Enable button only if both fields have text
            String phone = editTextPhone.getText().toString().trim();
            String message = editTextMessage.getText().toString().trim();
            boolean enabled = !phone.isEmpty() && !message.isEmpty();
            btnSend.setEnabled(enabled);
            btnSend.setAlpha(enabled ? 1f : 0.1f);
        }

        @Override
        public void afterTextChanged(Editable s) { }
    };

    private void resetForm() {

        editTextPhone.setText("");
        editTextMessage.setText("");

        // Reset stored date/time values
        selectedYear = 0;
        selectedMonth = 0;
        selectedDay = 0;
        selectedHour = 0;
        selectedMinute = 0;

        // Optional: reset any date/time display text
        textSelectedDateTime.setText(R.string.send_immediatly);

        // Disable button again
        btnSend.setEnabled(false);
        btnSend.setAlpha(0.5f);
        btnSend.setText(R.string.send);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}