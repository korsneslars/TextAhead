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
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.firstapp.messagesms.database.AppDatabase;

public class SmsWorker extends Worker {

    public SmsWorker(@NonNull Context context,
                     @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        String phone = getInputData().getString("phone");
        String message = getInputData().getString("message");

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phone, null, message, null, null);

            AppDatabase db = Room.databaseBuilder(
                    getApplicationContext(),
                    AppDatabase.class,
                    "sms_scheduler_db"
            ).allowMainThreadQueries().build();

            db.scheduledMessageDao().deleteByWorkId(getId().toString());

            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}
