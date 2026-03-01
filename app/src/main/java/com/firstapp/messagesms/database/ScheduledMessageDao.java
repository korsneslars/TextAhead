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

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduledMessageDao {

    @Insert
    void insert(ScheduledMessage message);

    @Delete
    void delete(ScheduledMessage message);

    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimeMillis ASC")
    List<ScheduledMessage> getAllMessages();

    @Query("DELETE FROM scheduled_messages WHERE workRequestId = :id")
    void deleteByWorkId(String id);

    @Query("SELECT COUNT(*) FROM scheduled_messages")
    int getPendingCount();
}
