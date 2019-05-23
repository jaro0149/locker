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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sk.jdecors.locker.ClassWithLocks.RecordEntry;

class ReadWriteLockerTest {

    private static final float WAITING_FACTOR = 20;
    private static final int THREAD_POOL_SIZE = 5;
    private static final ClassWithLocks ASPECT_TARGET = new ClassWithLocks();

    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    @AfterEach
    void clearTest() {
        ASPECT_TARGET.clearHistory();
    }

    @ParameterizedTest
    @MethodSource("provideTestData")
    void testLocking(final Runnable testedMethod, final int maximumWaitingDuration,
        final int expectedNumberOfRecordEntries, final Predicate<List<RecordEntry>> recordsVerification) {
        runThreads(testedMethod, maximumWaitingDuration);
        final List<RecordEntry> history = ASPECT_TARGET.getHistory();
        Assertions.assertEquals(expectedNumberOfRecordEntries, history.size());
        Assertions.assertTrue(recordsVerification.test(history));
    }

    private void runThreads(final Runnable method, final int maxWaitTime) {
        final List<? extends Future<?>> threadTokens = IntStream.range(0, THREAD_POOL_SIZE).boxed()
            .map(i -> executorService.submit(method))
            .collect(Collectors.toList());
        threadTokens.forEach(o -> {
            try {
                o.get(maxWaitTime, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                throw new IllegalStateException("Test was interrupted.", e);
            } catch (final ExecutionException e) {
                throw new IllegalStateException("An error occurred during execution of the thread.", e);
            } catch (final TimeoutException e) {
                throw new IllegalStateException(String.format("Test failed because of the exceeded maximum time " +
                    "of %s ms for completion of thread execution.", maxWaitTime), e);
            }
        });
    }

    private static Stream<Arguments> provideTestData() {
        return Stream.of(
            Arguments.of(
                (Runnable) ASPECT_TARGET::singleReadOnlyOperation,
                (int) (ClassWithLocks.SIMPLE_RO_TEST_TIMEOUT * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) recordEntry -> !areRecordsSequenced(recordEntry)),
            Arguments.of(
                (Runnable) ASPECT_TARGET::singleReadWriteOperation,
                (int) (ClassWithLocks.SIMPLE_RW_TEST_TIMEOUT * THREAD_POOL_SIZE * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) ReadWriteLockerTest::areRecordsSequenced),
            Arguments.of(
                (Runnable) ASPECT_TARGET::singleReadOnlyOperationWithMultipleLocks,
                (int) (ClassWithLocks.SIMPLE_RO_TEST_TIMEOUT * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) recordEntry -> !areRecordsSequenced(recordEntry)),
            Arguments.of(
                (Runnable) ASPECT_TARGET::singleReadWriteOperationWithMultipleLocks,
                (int) (ClassWithLocks.SIMPLE_RW_TEST_TIMEOUT * THREAD_POOL_SIZE * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) ReadWriteLockerTest::areRecordsSequenced),
            Arguments.of(
                (Runnable) ASPECT_TARGET::chainedReadOnlyOperation,
                (int) (ClassWithLocks.SIMPLE_RO_TEST_TIMEOUT * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) recordEntry -> !areRecordsSequenced(recordEntry)),
            Arguments.of(
                (Runnable) ASPECT_TARGET::chainedReadWriteOperation,
                (int) (ClassWithLocks.SIMPLE_RW_TEST_TIMEOUT * THREAD_POOL_SIZE * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) ReadWriteLockerTest::areRecordsSequenced),
            Arguments.of(
                (Runnable) ASPECT_TARGET::chainedReadOnlyOperationWithSplitLocks,
                (int) (ClassWithLocks.SIMPLE_RO_TEST_TIMEOUT * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) recordEntry -> !areRecordsSequenced(recordEntry)),
            Arguments.of(
                (Runnable) ASPECT_TARGET::chainedReadWriteOperationWithSplitLocks,
                (int) (ClassWithLocks.SIMPLE_RW_TEST_TIMEOUT * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) ReadWriteLockerTest::areRecordsSequenced),
            Arguments.of(
                (Runnable) ASPECT_TARGET::operationWithLocksStatedMultipleTimes,
                (int) (ClassWithLocks.SIMPLE_RW_TEST_TIMEOUT * THREAD_POOL_SIZE * WAITING_FACTOR),
                THREAD_POOL_SIZE * 2,
                (Predicate<List<RecordEntry>>) ReadWriteLockerTest::areRecordsSequenced)
        );
    }

    private static boolean areRecordsSequenced(final List<RecordEntry> history) {
        String lastSignature = null;
        for (final RecordEntry recordEntry : history) {
            final String actualSignature = recordEntry.getSignature();
            if (lastSignature != null && lastSignature.equals(actualSignature)) {
                return false;
            }
            lastSignature = actualSignature;
        }
        return true;
    }
}