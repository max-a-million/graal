/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotRuntime;

/**
 * A test of {@link AllocationReporter}.
 */
public class AllocationReporterTest {

    private TestAllocationReporter allocation;
    private PolyglotEngine engine;
    private PolyglotRuntime.Instrument instrument;

    @Before
    public void setUp() {
        PolyglotRuntime runtime = PolyglotRuntime.newBuilder().build();
        instrument = runtime.getInstruments().get("testAllocationReporter");
        instrument.setEnabled(true);
        allocation = instrument.lookup(TestAllocationReporter.class);
        engine = PolyglotEngine.newBuilder().runtime(runtime).build();
    }

    @After
    public void tearDown() {
        instrument.setEnabled(false);
        engine.dispose();
    }

    /**
     * Test that {@link AllocationReporter} receives events with complete information about value
     * allocations. We test events passed to both
     * {@link AllocationListener#onEnter(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object, long)}
     * and
     * {@link AllocationListener#onReturnValue(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Object, long)}
     * .
     */
    @Test
    public void testAllocationReport() {
        long u = AllocationReporter.SIZE_UNKNOWN;
        doTestAllocationReport(new long[]{u, 4, 8, 4, u}, new long[]{u, 4, 8, 4, 13});
    }

    private void doTestAllocationReport(long[] estimatedSizes, long[] computedSizes) {
        // Items to allocate:
        Source source = Source.newBuilder(
                        "NEW\n" +
                                        "10\n" +
                                        "12345678901234\n" +
                                        "-1000\n" +
                                        "8767584273645748301282734657402983457843901293874657867582034875\n").name("Allocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[0], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[0], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Integer", info.value.getClass().getName());
                            assertEquals(10, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 12345678901234
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[2], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Long", info.value.getClass().getName());
                            assertEquals(12345678901234L, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[2], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // -1000
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("java.lang.Integer", info.value.getClass().getName());
                            assertEquals(-1000, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 8767584273645748301282734657402983457843901293874657867582034875
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(estimatedSizes[4], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(0, info.oldSize);
                            assertEquals(computedSizes[4], info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        engine.eval(source);
        assertEquals(10, consumerCalls.get());
    }

    @Test
    public void testFailedAllocations() {
        Source source = Source.newBuilder("CanNotAllocateThisValue").name("FailedAllocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        try {
            engine.eval(source);
            fail();
        } catch (NumberFormatException ex) {
            // O.K.
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        source = Source.newBuilder("12345678901234").name("TooBigAllocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied allocation of 8 bytes.");
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        });
        try {
            engine.eval(source);
            fail();
        } catch (OutOfMemoryError ex) {
            // O.K.
            assertEquals("Denied allocation of 8 bytes.", ex.getMessage());
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        source = Source.newBuilder("12345678901234->9876758023873465783492873465784938746502897345634897856").name("TooBigReallocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers(
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied an unknown reallocation.");
                        },
                        (info) -> {
                            consumerCalls.incrementAndGet();
                        });
        try {
            engine.eval(source);
            fail();
        } catch (OutOfMemoryError ex) {
            assertEquals("Denied an unknown reallocation.", ex.getMessage());
        }
        assertEquals(1, consumerCalls.get());
    }

    @Test
    @Ignore
    public void testWrongAllocations() {
        // Test of wrong allocation reports
        // A call to allocated() without a prior notifyWill... fails:
        Source source = Source.newBuilder("WRONG").name("AllocatedWithoutWill").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers((info) -> consumerCalls.incrementAndGet());
        try {
            engine.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("onEnter() was not called", err.getMessage());
        }
        assertEquals(0, consumerCalls.get());

        // Have one notifyWillReallocate() call caused by NEW,
        // but denied to suppress the notifyAllocated().
        // Then call notifyAllocated() alone:
        source = Source.newBuilder("10->10").name("willReallocate").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers(
                        (info) -> {
                            consumerCalls.incrementAndGet();
                            throw new OutOfMemoryError("Denied one allocation.");
                        });
        try {
            engine.eval(source);
            fail();
        } catch (OutOfMemoryError err) {
            // O.K.
        }
        assertEquals(1, consumerCalls.get());
        source = Source.newBuilder("WRONG").name("AllocatedAfterDifferentWill").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        try {
            engine.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("A different reallocated value. Was: 10 now is: NewObject", err.getMessage());
        }
        assertEquals(1, consumerCalls.get());
        consumerCalls.set(0);

        // Exposal of internal values is not allowed
        source = Source.newBuilder("INTERNAL").name("AllocatedInternalValue").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        try {
            engine.eval(source);
            fail();
        } catch (AssertionError err) {
            assertEquals("Wrong value class, TruffleObject is required. Was: " + AllocationReporterLanguage.AllocValue.class.getName(), err.getMessage());
        }
        assertEquals(1, consumerCalls.get());
    }

    @Test
    public void testReallocationReport() {
        long u = AllocationReporter.SIZE_UNKNOWN;
        doTestReallocationReport(new long[]{u, 4, 13, u}, new long[]{u, 4, 13, 6});
    }

    private void doTestReallocationReport(long[] estimatedSizes, long[] computedSizes) {
        Source source = Source.newBuilder(
                        "NEW->10\n" + "8767584273645748301282734657402983457843901293874657867582034875->987364758928736457840187265789\n").name("Allocations").mimeType(
                                        AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW -> 10
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(estimatedSizes[0], info.oldSize);
                            assertEquals(estimatedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            assertEquals(computedSizes[0], info.oldSize);
                            assertEquals(computedSizes[1], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // BigNumber -> BigNumber
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(estimatedSizes[2], info.oldSize);
                            assertEquals(estimatedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(BigNumber.class, info.value.getClass());
                            assertEquals(computedSizes[2], info.oldSize);
                            assertEquals(computedSizes[3], info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        engine.eval(source);
        assertEquals(4, consumerCalls.get());
    }

    @Test
    public void testNestedAllocations() {
        Source source = Source.newBuilder(
                        "NEW { NEW }\n" +
                                        "10 { 20 30 { 1234567890123456789 } }\n" +
                                        "12345678901234->897654123210445621235489 { 10->NEW { 20->NEW } 30->NEW }\n").name("NestedAllocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        // NEW { NEW }
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals("NewObject", info.value.toString());
                            consumerCalls.incrementAndGet();
                        },
                        // | 10 { 20 ...
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { | 20 ...
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 | 30 ...
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(20, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { | 1234567890123456789 } }
                        (info) -> {
                            assertTrue(info.will);
                            assertNull(info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 | } }
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(1234567890123456789L, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(8, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 } | }
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(30, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // 10 { 20 30 { 1234567890123456789 } } |
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(10, info.value);
                            assertEquals(0, info.oldSize);
                            assertEquals(4, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        // | 12345678901234->897654123210445621235489 { 10->NEW { 20->NEW } 30->NEW
                        // }
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(10, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(20, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(20, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(10, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertTrue(info.will);
                            assertEquals(30, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(30, info.value);
                            assertEquals(4, info.oldSize);
                            assertEquals(AllocationReporter.SIZE_UNKNOWN, info.newSize);
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> {
                            assertFalse(info.will);
                            assertEquals(12345678901234L, info.value);
                            assertEquals(8, info.oldSize);
                            assertEquals(5, info.newSize);
                            consumerCalls.incrementAndGet();
                        });
        engine.eval(source);
        assertEquals(20, consumerCalls.get());
    }

    @Test
    public void testUnregister() {
        Source source = Source.newBuilder(
                        "NEW\n" +
                                        "10\n" +
                                        "12345678901234\n" +
                                        "-1000\n" +
                                        "8767584273645748301282734657402983457843901293874657867582034875\n").name("Allocations").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        AtomicInteger consumerCalls = new AtomicInteger(0);
        allocation.setAllocationConsumers(
                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> {
                            allocation.getAllocationEventBinding().dispose();
                            consumerCalls.incrementAndGet();
                        },
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet(),

                        (info) -> consumerCalls.incrementAndGet(),
                        (info) -> consumerCalls.incrementAndGet());
        engine.eval(source);
        assertEquals(3, consumerCalls.get());
    }

    @Test
    public void testReporterChangeListener() {
        try {
            Class.forName("java.beans.PropertyChangeListener");
        } catch (ClassNotFoundException ex) {
            // skip the test if running only with java.base JDK9 module
            return;
        }

        // Test of AllocationReporter property change listener notifications
        instrument.setEnabled(false);
        Source source = Source.newBuilder("NEW").name("AllocateLanguageWakeUp").mimeType(AllocationReporterLanguage.MIME_TYPE).build();
        allocation.setAllocationConsumers((info) -> {
        }, (info) -> {
        });
        engine.eval(source);
        AllocationReporter reporter = (AllocationReporter) engine.findGlobalSymbol(AllocationReporter.class.getSimpleName()).get();
        AtomicInteger listenerCalls = new AtomicInteger(0);
        AllocationReporterListener activatedListener = AllocationReporterListener.register(listenerCalls, reporter);
        assertEquals(0, listenerCalls.get());
        assertFalse(reporter.isActive());
        instrument.setEnabled(true);
        assertEquals(1, listenerCalls.get());
        activatedListener.unregister();
        listenerCalls.set(0);

        AllocationDeactivatedListener deactivatedListener = AllocationDeactivatedListener.register(listenerCalls, reporter);
        assertEquals(0, listenerCalls.get());
        assertTrue(reporter.isActive());
        instrument.setEnabled(false);
        assertEquals(1, listenerCalls.get());
        deactivatedListener.unregister();
    }

    /**
     * A test allocation language. Parses allocation commands separated by white spaces.
     * <ul>
     * <li>new - allocation of an unknown size</li>
     * <li>&lt;int number&gt; - allocation of an integer (4 bytes)</li>
     * <li>&lt;long number&gt; - allocation of a long (8 bytes)</li>
     * <li>&lt;big number&gt; - allocation of a big number (unknown size in advance, computed from
     * BigInteger bit length afterwards)</li>
     * <li>&lt;command&gt;-&gt;&lt;command&gt; - re-allocation</li>
     * <li>{ &lt;command&gt; ... } allocations nested under the previous command</li>
     * </ul>
     */
    @TruffleLanguage.Registration(mimeType = AllocationReporterLanguage.MIME_TYPE, name = "Allocation Reporter Language", version = "1.0")
    public static class AllocationReporterLanguage extends TruffleLanguage<AllocationReporter> {

        public static final String MIME_TYPE = "application/x-truffle-allocation-reporter-language";
        public static final String PROP_SIZE_CALLS = "sizeCalls";

        @Override
        protected AllocationReporter createContext(Env env) {
            return env.lookup(AllocationReporter.class);
        }

        @Override
        protected Object findExportedSymbol(AllocationReporter context, String globalName, boolean onlyExplicit) {
            if (AllocationReporter.class.getSimpleName().equals(globalName)) {
                return context;
            }
            return null;
        }

        @Override
        protected Object getLanguageGlobal(AllocationReporter context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final Source code = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Node.Child private AllocNode alloc = parse(code.getCode());

                @Override
                public Object execute(VirtualFrame frame) {
                    return alloc.execute(frame);
                }

            });
        }

        private AllocNode parse(String code) {
            String[] allocations = code.split("\\s");
            LinkedList<FutureNode> futures = new LinkedList<>();
            FutureNode parent = new FutureNode(null, null);
            FutureNode last = null;
            futures.add(parent);
            for (String allocCommand : allocations) {
                if ("{".equals(allocCommand)) {
                    futures.add(last);
                    parent = last;
                    last = null;
                    continue;
                }
                if (last != null) {
                    parent.addChild(last.toNode(getContextReference()));
                    last = null;
                }
                if ("}".equals(allocCommand)) {
                    AllocNode node = parent.toNode(getContextReference());
                    futures.removeLast(); // the "parent" removed
                    parent = futures.getLast();
                    parent.addChild(node);
                    continue;
                }
                int reallocIndex = allocCommand.indexOf("->");
                if (reallocIndex < 0) { // pure allocation
                    AllocValue newValue = parseValue(allocCommand);
                    last = new FutureNode(null, newValue);
                } else {
                    AllocValue oldValue = parseValue(allocCommand.substring(0, reallocIndex));
                    AllocValue newValue = parseValue(allocCommand.substring(reallocIndex + 2));
                    last = new FutureNode(oldValue, newValue);
                }
            }
            if (last != null) {
                parent.addChild(last.toNode(getContextReference()));
            }
            return futures.removeLast().toNode(getContextReference());
        }

        private static AllocValue parseValue(String allocCommand) {
            AllocValue newValue;
            if ("new".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.UNKNOWN, null);
            } else if ("internal".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.INTERNAL, null);
            } else if ("wrong".equalsIgnoreCase(allocCommand)) {
                newValue = new AllocValue(AllocValue.Kind.WRONG, null);
            } else {
                try {
                    Integer.parseInt(allocCommand);
                    newValue = new AllocValue(AllocValue.Kind.INT, allocCommand);
                } catch (NumberFormatException exi) {
                    try {
                        Long.parseLong(allocCommand);
                        newValue = new AllocValue(AllocValue.Kind.LONG, allocCommand);
                    } catch (NumberFormatException exl) {
                        newValue = new AllocValue(AllocValue.Kind.BIG, allocCommand);
                    }
                }
            }
            return newValue;
        }

        private static class AllocValue {

            enum Kind {
                UNKNOWN,
                INT,
                LONG,
                BIG,
                INTERNAL,   // Exposes an internal object impl (for error handling test)
                WRONG,      // Test of a wrong allocation report
            }

            final Kind kind;
            final String text;

            AllocValue(Kind kind, String text) {
                this.kind = kind;
                this.text = text;
            }
        }

        private static class FutureNode {

            private final AllocValue oldValue;
            private final AllocValue newValue;
            private List<AllocNode> children;

            FutureNode(AllocValue oldValue, AllocValue newValue) {
                this.oldValue = oldValue;
                this.newValue = newValue;
            }

            void addChild(AllocNode node) {
                if (children == null) {
                    children = new ArrayList<>();
                }
                children.add(node);
            }

            AllocNode toNode(ContextReference<AllocationReporter> contextRef) {
                if (children == null) {
                    return new AllocNode(oldValue, newValue, contextRef);
                } else {
                    return new AllocNode(oldValue, newValue, contextRef, children.toArray(new AllocNode[children.size()]));
                }
            }
        }

        private static class AllocNode extends Node {

            private final AllocValue oldValue;
            private final AllocValue newValue;
            private final ContextReference<AllocationReporter> contextRef;
            @Children private final AllocNode[] children;

            AllocNode(AllocValue oldValue, AllocValue newValue, ContextReference<AllocationReporter> contextRef) {
                this(oldValue, newValue, contextRef, null);
            }

            AllocNode(AllocValue oldValue, AllocValue newValue, ContextReference<AllocationReporter> contextRef, AllocNode[] children) {
                this.oldValue = oldValue;
                this.newValue = newValue;
                this.contextRef = contextRef;
                this.children = children;
            }

            public Object execute(VirtualFrame frame) {
                Object value;
                if (newValue == null) { // No allocation
                    value = null;
                    execChildren(frame);
                } else if (oldValue == null) {
                    // new allocation
                    if (contextRef.get().isActive()) {
                        if (newValue.kind != AllocValue.Kind.WRONG) {
                            // Test that it's wrong not to report will allocate
                            contextRef.get().onEnter(null, 0, getAllocationSizeEstimate(newValue));
                        }
                    }
                    execChildren(frame);
                    value = allocateValue(newValue);
                    if (contextRef.get().isActive()) {
                        contextRef.get().onReturnValue(value, 0, computeValueSize(newValue, value));
                    }
                } else {
                    // re-allocation
                    value = allocateValue(oldValue);    // pretend that it was allocated already
                    long oldSize = AllocationReporter.SIZE_UNKNOWN;
                    long newSize = AllocationReporter.SIZE_UNKNOWN;
                    if (contextRef.get().isActive()) {
                        oldSize = computeValueSize(oldValue, value);
                        newSize = getAllocationSizeEstimate(newValue);
                        contextRef.get().onEnter(value, oldSize, newSize);
                    }
                    execChildren(frame);
                    // Re-allocate, oldValue -> newValue
                    if (contextRef.get().isActive()) {
                        if (newSize == AllocationReporter.SIZE_UNKNOWN) {
                            if (AllocValue.Kind.BIG == newValue.kind) {
                                newSize = ((BigNumber) allocateValue(newValue)).getSize();
                            } else {
                                newSize = getAllocationSizeEstimate(newValue);
                            }
                        }
                        contextRef.get().onReturnValue(value, oldSize, newSize);
                    }
                }
                return value;
            }

            private void execChildren(VirtualFrame frame) {
                if (children != null) {
                    for (AllocNode ch : children) {
                        ch.execute(frame);
                    }
                }
            }

            private static long getAllocationSizeEstimate(AllocValue value) {
                switch (value.kind) {
                    case INT:
                        return 4;
                    case LONG:
                        return 8;
                    case BIG:
                    case UNKNOWN:
                    default:
                        return AllocationReporter.SIZE_UNKNOWN;
                }
            }

            private static long computeValueSize(AllocValue aValue, Object value) {
                switch (aValue.kind) {
                    case INT:
                        return 4;
                    case LONG:
                        return 8;
                    case BIG:
                        return ((BigNumber) value).getSize();
                    case UNKNOWN:
                    default:
                        return AllocationReporter.SIZE_UNKNOWN;
                }
            }

            private static Object allocateValue(AllocValue value) {
                switch (value.kind) {
                    case INT:
                        return Integer.parseInt(value.text);
                    case LONG:
                        return Long.parseLong(value.text);
                    case BIG:
                        return new BigNumber(value.text);
                    case INTERNAL:
                        return value;   // to test that it's wrong to expose an internal object
                    case UNKNOWN:
                    default:
                        return new TruffleObject() {
                            @Override
                            public ForeignAccess getForeignAccess() {
                                // For tests only
                                return null;
                            }

                            @Override
                            public String toString() {
                                return "NewObject";
                            }
                        };
                }
            }

        }
    }

    private static class BigNumber implements TruffleObject {

        private BigInteger integer;

        BigNumber(String value) {
            this.integer = new BigInteger(value);
        }

        long getSize() {
            return integer.bitCount() / 8;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            // For test only
            return null;
        }
    }

    private static final class AllocationInfo {

        private final Object value;
        private final long oldSize;
        private final long newSize;
        private final boolean will;

        private AllocationInfo(AllocationEvent event, boolean will) {
            this.value = event.getValue();
            this.oldSize = event.getOldSize();
            this.newSize = event.getNewSize();
            this.will = will;
        }
    }

    @TruffleInstrument.Registration(id = "testAllocationReporter")
    public static class TestAllocationReporter extends TruffleInstrument implements AllocationListener {

        private EventBinding<TestAllocationReporter> allocationEventBinding;
        private Consumer<AllocationInfo>[] allocationConsumers;
        private int consumersIndex = 0;

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            env.registerService(this);
            LanguageInfo testLanguage = env.getLanguages().get(AllocationReporterLanguage.MIME_TYPE);
            allocationEventBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.newBuilder().languages(testLanguage).build(), this);
        }

        @Override
        protected void onDispose(Env env) {
            allocationEventBinding.dispose();
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        final void setAllocationConsumers(Consumer<AllocationInfo>... allocationConsumers) {
            consumersIndex = 0;
            this.allocationConsumers = allocationConsumers;
        }

        EventBinding<TestAllocationReporter> getAllocationEventBinding() {
            return allocationEventBinding;
        }

        @Override
        @TruffleBoundary
        public void onEnter(AllocationEvent event) {
            Consumer<AllocationInfo> consumer = allocationConsumers[consumersIndex++];
            consumer.accept(new AllocationInfo(event, true));
        }

        @Override
        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            Consumer<AllocationInfo> consumer = allocationConsumers[consumersIndex++];
            consumer.accept(new AllocationInfo(event, false));
        }

    }

}
