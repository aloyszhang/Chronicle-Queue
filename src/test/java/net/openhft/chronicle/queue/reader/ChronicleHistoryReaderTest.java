/*
 * Copyright 2016-2020 chronicle.software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.openhft.chronicle.queue.reader;

import net.openhft.chronicle.bytes.MethodReader;
import net.openhft.chronicle.core.io.IOTools;
import net.openhft.chronicle.core.util.Histogram;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.QueueTestCommon;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueue;
import net.openhft.chronicle.wire.MessageHistory;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChronicleHistoryReaderTest extends QueueTestCommon {

    @Test
    public void testWithQueueHistoryRecordHistoryInitial() {

        int extraTiming = 1;
        File queuePath1 = IOTools.createTempFile("testWithQueueHistory1-");
        File queuePath2 = IOTools.createTempFile("testWithQueueHistory2-");
        File queuePath3 = IOTools.createTempFile("testWithQueueHistory3-");
        try {
            try (ChronicleQueue out = queue(queuePath1, 1)) {
                DummyListener writer = out.acquireAppender()
                        .methodWriterBuilder(DummyListener.class)
                        .get();
                // this will write the 1st timestamps
                writer.say("hello");
            }

            try (ChronicleQueue in = queue(queuePath1, 1);
                 ChronicleQueue out = queue(queuePath2, 2)) {
                DummyListener writer = out.acquireAppender()
                        .methodWriterBuilder(DummyListener.class)
                        .get();
                DummyListener dummy = msg -> {
                    MessageHistory history = MessageHistory.get();
                    Assert.assertEquals(1, history.sources());
                    // written 1st then received by me
                    Assert.assertEquals(1 + extraTiming, history.timings());
                    // this writes 2 more timestamps
                    writer.say(msg);
                };
                MethodReader reader = in.createTailer().methodReader(dummy);
                assertTrue(reader.readOne());
                assertFalse(reader.readOne());
            }

            try (ChronicleQueue in = queue(queuePath2, 2);
                 ChronicleQueue out = queue(queuePath3, 3)) {
                DummyListener writer = out.acquireAppender()
                        .methodWriterBuilder(DummyListener.class)
                        .get();
                DummyListener dummy = msg -> {
                    MessageHistory history = MessageHistory.get();
                    Assert.assertEquals(2, history.sources());
                    Assert.assertEquals(3 + extraTiming, history.timings());
                    // this writes 2 more timestamps
                    writer.say(msg);
                };
                MethodReader reader = in.createTailer().methodReader(dummy);
                assertTrue(reader.readOne());
                assertFalse(reader.readOne());
            }

            ChronicleHistoryReader chronicleHistoryReader = new ChronicleHistoryReader()
                    .withBasePath(queuePath3.toPath())
                    .withTimeUnit(TimeUnit.MICROSECONDS)
                    .withMessageSink(System.out::println);
            Map<String, Histogram> histos = chronicleHistoryReader.readChronicle();
            chronicleHistoryReader.outputData();

            Assert.assertEquals(5, histos.size());
            Assert.assertEquals("[1, startTo1, 2, 1to2, endToEnd]", histos.keySet().toString());
        } finally {
            IOTools.deleteDirWithFiles(queuePath1.toString(), queuePath2.toString(), queuePath3.toString());
        }
    }

    @NotNull
    private SingleChronicleQueue queue(File queuePath1, int sourceId) {
        return ChronicleQueue.singleBuilder(queuePath1).testBlockSize().sourceId(sourceId).build();
    }

    @FunctionalInterface
    interface DummyListener {
        void say(String what);
    }
}