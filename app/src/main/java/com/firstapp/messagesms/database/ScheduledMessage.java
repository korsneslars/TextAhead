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

package com.firstapp.messagesms.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scheduled_messages")
public class ScheduledMessage {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String phone;

    public String contactName; // optional

    @NonNull
    public String message;

    public long scheduledTimeMillis;

    public String workRequestId; // optional, for canceling WorkManager

    public ScheduledMessage() {
        phone = "";
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public String getMessage() {
        return message;
    }

    public long getScheduledTimeMillis() {
        return scheduledTimeMillis;
    }
}
