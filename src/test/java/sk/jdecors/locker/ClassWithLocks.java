/* Copyright (C) 2019 Jaroslav Tóth

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.*/

package sk.jdecors.locker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import sk.jdecors.locker.annotations.LockedForRead;
import sk.jdecors.locker.annotations.LockedForWrite;

final class ClassWithLocks {

    static final class RecordEntry {
        private final Thread thread;
        private final String signature;

        private RecordEntry(final Thread thread, final String signature) {
            this.thread = thread;
            this.signature = signature;
        }

        Thread getThread() {
            return thread;
        }

        String getSignature() {
            return signature;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("RecordEntry{");
            sb.append("thread=").append(thread);
            sb.append(", signature='").append(signature).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    static final int SIMPLE_RO_TEST_TIMEOUT = 50;
    static final int SIMPLE_RW_TEST_TIMEOUT = 10;

    private static final String SIMPLE_TEST_START_SIGNATURE = "simple_start";
    private static final String SIMPLE_TEST_STOP_SIGNATURE = "simple_stop";

    private final List<RecordEntry> history = new CopyOnWriteArrayList<>();

    @LockedForRead
    void singleReadOnlyOperation() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            TimeUnit.MILLISECONDS.sleep(SIMPLE_RO_TEST_TIMEOUT);
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Read-only operation was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForWrite
    void singleReadWriteOperation() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            TimeUnit.MILLISECONDS.sleep(SIMPLE_RW_TEST_TIMEOUT);
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Read-write operation was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForRead(stamp = {1, 2, 3, 4, 5})
    void singleReadOnlyOperationWithMultipleLocks() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            TimeUnit.MILLISECONDS.sleep(SIMPLE_RO_TEST_TIMEOUT);
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Read-only operation with multiple locks was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForWrite(stamp = {1, 2, 3, 4, 5})
    void singleReadWriteOperationWithMultipleLocks() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            TimeUnit.MILLISECONDS.sleep(SIMPLE_RW_TEST_TIMEOUT);
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Read-write operation with multiple locks was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForRead
    void chainedReadOnlyOperation() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            chainedReadOnlyOperationPart();
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Chained read-only operation was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForRead
    private void chainedReadOnlyOperationPart() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(SIMPLE_RO_TEST_TIMEOUT);
    }

    @LockedForWrite
    void chainedReadWriteOperation() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            chainedReadWriteOperationPart();
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Chained read-write operation was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForWrite
    private void chainedReadWriteOperationPart() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(SIMPLE_RW_TEST_TIMEOUT);
    }

    @LockedForRead(stamp = {10, 15, 20})
    void chainedReadOnlyOperationWithSplitLocks() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            chainedReadOnlyOperationPartWithSplitLocks();
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Chained read-only operation with split locks was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForWrite(stamp = {15, 20, 30})
    private void chainedReadOnlyOperationPartWithSplitLocks() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(SIMPLE_RO_TEST_TIMEOUT);
    }

    @LockedForWrite(stamp = {0, 10, 20})
    void chainedReadWriteOperationWithSplitLocks() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            chainedReadWriteOperationPartWithSplitLocks();
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Chained read-write operation with split locks was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    @LockedForWrite(stamp = {0, 30, 10})
    private void chainedReadWriteOperationPartWithSplitLocks() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(SIMPLE_RW_TEST_TIMEOUT);
    }

    @LockedForWrite(stamp = {11, 3, 11, 3, 11})
    void operationWithLocksStatedMultipleTimes() {
        addHistoryEntry(SIMPLE_TEST_START_SIGNATURE);
        try {
            TimeUnit.MILLISECONDS.sleep(SIMPLE_RW_TEST_TIMEOUT);
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Read-write operation with locks state multiple times was interrupted.", e);
        }
        addHistoryEntry(SIMPLE_TEST_STOP_SIGNATURE);
    }

    private void addHistoryEntry(final String signature) {
        history.add(new RecordEntry(Thread.currentThread(), signature));
    }

    List<RecordEntry> getHistory() {
        return new ArrayList<>(history);
    }

    void clearHistory() {
        history.clear();
    }
}