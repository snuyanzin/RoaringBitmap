/*
 * Copyright 2013-2014 by Daniel Lemire, Owen Kaser and Samy Chambi
 * Licensed under the Apache License, Version 2.0.
 */
package org.roaringbitmap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Iterator;

/**
 * RoaringBitmap, a compressed alternative to the BitSet.
 * 
 */
public final class RoaringBitmap implements Cloneable, Serializable,
        Iterable<Integer>, Externalizable {

        private static final long serialVersionUID = 6L;

        /**
         * Bitwise AND (intersection) operation. The provided bitmaps are *not*
         * modified. This operation is thread-safe as long as the provided
         * bitmaps remain unchanged.
         * 
         * If you have more than 2 bitmaps, consider using the 
         * FastAggregation class.
         * 
         * @param x1
         *                first bitmap
         * @param x2
         *                other bitmap
         * @return result of the operation
         */
        public static RoaringBitmap and(final RoaringBitmap x1,
                final RoaringBitmap x2) {
                final RoaringBitmap answer = new RoaringBitmap();
                int pos1 = 0, pos2 = 0;
                final int length1 = x1.highlowcontainer.size(), length2 = x2.highlowcontainer
                        .size();
                /*
                 * TODO: This could be optimized quite a bit when one bitmap is
                 * much smaller than the other one.
                 */
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = x1.highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);
                        do {
                                if (s1 < s2) {
                                        pos1++;
                                        if (pos1 == length1)
                                                break main;
                                        s1 = x1.highlowcontainer.getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        pos2++;
                                        if (pos2 == length2)
                                                break main;
                                        s2 = x2.highlowcontainer.getKeyAtIndex(pos2);
                                } else {
                                        final Container C = x1.highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .and(x2.highlowcontainer
                                                        .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0)
                                                answer.highlowcontainer.append(
                                                        s1, C);
                                        pos1++;
                                        pos2++;
                                        if ((pos1 == length1) || (pos2 == length2))
                                                break main;
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        } while (true);
                }
                return answer;
        }

        /**
         * Bitwise ANDNOT (difference) operation. The provided bitmaps are *not*
         * modified. This operation is thread-safe as long as the provided
         * bitmaps remain unchanged.
         * 
         * @param x1
         *                first bitmap
         * @param x2
         *                other bitmap
         * @return result of the operation
         */
        public static RoaringBitmap andNot(final RoaringBitmap x1,
                final RoaringBitmap x2) {
                final RoaringBitmap answer = new RoaringBitmap();
                int pos1 = 0, pos2 = 0;
                final int length1 = x1.highlowcontainer.size(), length2 = x2.highlowcontainer
                        .size();
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = x1.highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);
                        do {
                                if (s1 < s2) {
                                        answer.highlowcontainer.appendCopy(
                                                x1.highlowcontainer, pos1);
                                        pos1++;
                                        if (pos1 == length1)
                                                break main;
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        final Container C = x1.highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .andNot(x2.highlowcontainer
                                                        .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0)
                                                answer.highlowcontainer.append(
                                                        s1, C);
                                        pos1++;
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2))
                                                break main;
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        } while (true);
                }
                if (pos2 == length2) {
                        answer.highlowcontainer.appendCopy(x1.highlowcontainer,
                                pos1, length1);
                }
                return answer;
        }

        /**
         * Generate a bitmap with the specified values set to true. The provided
         * integers values don't have to be in sorted order, but it may be
         * preferable to sort them from a perfomance point of view.
         * 
         * @param dat
         *                set values
         * @return a new bitmap
         */
        public static RoaringBitmap bitmapOf(final int... dat) {
                final RoaringBitmap ans = new RoaringBitmap();
                for (final int i : dat)
                        ans.add(i);
                return ans;
        }

        /**
         * Complements the bits in the given range, from rangeStart (inclusive)
         * rangeEnd (exclusive). The given bitmap is unchanged.
         * 
         * @param bm
         *                bitmap being negated
         * @param rangeStart
         *                inclusive beginning of range
         * @param rangeEnd
         *                exclusive ending of range
         * @return a new Bitmap
         */
        public static RoaringBitmap flip(RoaringBitmap bm,
                final int rangeStart, final int rangeEnd) {
                RoaringBitmap answer = null;
                if (rangeStart >= rangeEnd) {
                        answer = bm.clone();
                        // System.out.println("cloned!");
                        return answer;
                }

                answer = new RoaringBitmap();
                final short hbStart = Util.highbits(rangeStart);
                final short lbStart = Util.lowbits(rangeStart);
                final short hbLast = Util.highbits(rangeEnd - 1);
                final short lbLast = Util.lowbits(rangeEnd - 1);

                // copy the containers before the active area
                answer.highlowcontainer.appendCopiesUntil(bm.highlowcontainer,
                        hbStart);

                int max = Util.toIntUnsigned(Util.maxLowBit());
                for (short hb = hbStart; hb <= hbLast; ++hb) {
                        final int containerStart = (hb == hbStart) ? Util
                                .toIntUnsigned(lbStart) : 0;
                        final int containerLast = (hb == hbLast) ? Util
                                .toIntUnsigned(lbLast) : max;

                        final int i = bm.highlowcontainer.getIndex(hb);
                        final int j = answer.highlowcontainer.getIndex(hb);
                        assert j < 0;

                        if (i >= 0) {
                                Container c = bm.highlowcontainer
                                        .getContainerAtIndex(i).not(
                                                containerStart, containerLast);
                                if (c.getCardinality() > 0)
                                        answer.highlowcontainer
                                                .insertNewKeyValueAt(-j - 1,
                                                        hb, c);

                        } else { // *think* the range of ones must never be
                                 // empty.
                                answer.highlowcontainer.insertNewKeyValueAt(
                                        -j - 1, hb, Container.rangeOfOnes(
                                                containerStart, containerLast));
                        }
                }
                // copy the containers after the active area.
                answer.highlowcontainer.appendCopiesAfter(bm.highlowcontainer,
                        hbLast);

                return answer;
        }

        /**
         * Bitwise OR (union) operation. The provided bitmaps are *not*
         * modified. This operation is thread-safe as long as the provided
         * bitmaps remain unchanged.
         * 
         * If you have more than 2 bitmaps, consider using the 
         * FastAggregation class.
         * 
         * @param x1
         *                first bitmap
         * @param x2
         *                other bitmap
         * @return result of the operation
         */
        public static RoaringBitmap or(final RoaringBitmap x1,
                final RoaringBitmap x2) {
                final RoaringBitmap answer = new RoaringBitmap();
                int pos1 = 0, pos2 = 0;
                final int length1 = x1.highlowcontainer.size(), length2 = x2.highlowcontainer
                        .size();
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = x1.highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);

                        while (true) {
                                if (s1 < s2) {
                                        answer.highlowcontainer.appendCopy(
                                                x1.highlowcontainer, pos1);
                                        pos1++;
                                        if (pos1 == length1) {
                                                break main;
                                        }
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        answer.highlowcontainer.appendCopy(
                                                x2.highlowcontainer, pos2);
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        answer.highlowcontainer
                                                .append(s1,
                                                        x1.highlowcontainer
                                                                .getContainerAtIndex(
                                                                        pos1)
                                                                .or(x2.highlowcontainer
                                                                        .getContainerAtIndex(pos2)));
                                        pos1++;
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2)) {
                                                break main;
                                        }
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        }
                }
                if (pos1 == length1) {
                        answer.highlowcontainer.appendCopy(x2.highlowcontainer,
                                pos2, length2);
                } else if (pos2 == length2) {
                        answer.highlowcontainer.appendCopy(x1.highlowcontainer,
                                pos1, length1);
                }
                return answer;
        }

        /**
         * Bitwise XOR (symmetric difference) operation. The provided bitmaps
         * are *not* modified. This operation is thread-safe as long as the
         * provided bitmaps remain unchanged.
         * 
         * If you have more than 2 bitmaps, consider using the 
         * FastAggregation class.
         * 
         * @param x1
         *                first bitmap
         * @param x2
         *                other bitmap
         * @return result of the operation
         */
        public static RoaringBitmap xor(final RoaringBitmap x1,
                final RoaringBitmap x2) {
                final RoaringBitmap answer = new RoaringBitmap();
                int pos1 = 0, pos2 = 0;
                final int length1 = x1.highlowcontainer.size(), length2 = x2.highlowcontainer
                        .size();

                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = x1.highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);

                        while (true) {
                                if (s1 < s2) {
                                        answer.highlowcontainer.appendCopy(
                                                x1.highlowcontainer, pos1);
                                        pos1++;
                                        if (pos1 == length1) {
                                                break main;
                                        }
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        answer.highlowcontainer.appendCopy(
                                                x2.highlowcontainer, pos2);
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        final Container C = x1.highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .xor(x2.highlowcontainer
                                                        .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0)
                                                answer.highlowcontainer.append(
                                                        s1, C);
                                        pos1++;
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2)) {
                                                break main;
                                        }
                                        s1 = x1.highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        }
                }
                if (pos1 == length1) {
                        answer.highlowcontainer.appendCopy(x2.highlowcontainer,
                                pos2, length2);
                } else if (pos2 == length2) {
                        answer.highlowcontainer.appendCopy(x1.highlowcontainer,
                                pos1, length1);
                }

                return answer;
        }

        protected RoaringArray highlowcontainer = null;

        /**
         * Create an empty bitmap
         */
        public RoaringBitmap() {
                highlowcontainer = new RoaringArray();
        }

        /**
         * set the value to "true", whether it already appears or not.
         * 
         * @param x
         *                integer value
         */
        public void add(final int x) {
                final short hb = Util.highbits(x);
                final int i = highlowcontainer.getIndex(hb);
                if (i >= 0) {
                        highlowcontainer.setContainerAtIndex(
                                i,
                                highlowcontainer.getContainerAtIndex(i).add(
                                        Util.lowbits(x)));
                } else {
                        final ArrayContainer newac = new ArrayContainer();
                        highlowcontainer.insertNewKeyValueAt(-i - 1, hb,
                                newac.add(Util.lowbits(x)));
                }
        }

        /**
         * In-place bitwise AND (intersection) operation. The current bitmap is
         * modified.
         * 
         * @param x2
         *                other bitmap
         */
        public void and(final RoaringBitmap x2) {
                int pos1 = 0, pos2 = 0;
                int length1 = highlowcontainer.size();
                final int length2 = x2.highlowcontainer.size();
                /*
                 * TODO: This could be optimized quite a bit when one bitmap is
                 * much smaller than the other one.
                 */
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);
                        do {
                                if (s1 < s2) {
                                        highlowcontainer.removeAtIndex(pos1);
                                        --length1;
                                        if (pos1 == length1)
                                                break main;
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        pos2++;
                                        if (pos2 == length2)
                                                break main;
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        final Container C = highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .iand(x2.highlowcontainer
                                                        .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0) {
                                                this.highlowcontainer
                                                        .setContainerAtIndex(
                                                                pos1, C);
                                                pos1++;
                                        } else {
                                                highlowcontainer
                                                        .removeAtIndex(pos1);
                                                --length1;
                                        }
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2))
                                                break main;
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        } while (true);
                }
                highlowcontainer.resize(pos1);
        }

        /**
         * In-place bitwise ANDNOT (difference) operation. The current bitmap is
         * modified.
         * 
         * @param x2
         *                other bitmap
         */
        public void andNot(final RoaringBitmap x2) {
                int pos1 = 0, pos2 = 0;
                int length1 = highlowcontainer.size();
                final int length2 = x2.highlowcontainer.size();
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);
                        do {
                                if (s1 < s2) {
                                        pos1++;
                                        if (pos1 == length1)
                                                break main;
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        final Container C = highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .iandNot(
                                                        x2.highlowcontainer
                                                                .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0) {
                                                this.highlowcontainer
                                                        .setContainerAtIndex(
                                                                pos1, C);
                                                pos1++;
                                        } else {
                                                highlowcontainer
                                                        .removeAtIndex(pos1);
                                                --length1;
                                        }
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2))
                                                break main;
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        } while (true);
                }
        }

        /**
         * reset to an empty bitmap; result occupies as much space a newly
         * created bitmap.
         */
        public void clear() {
                highlowcontainer = new RoaringArray(); // lose references
        }

        @Override
        public RoaringBitmap clone() {
                try {
                        final RoaringBitmap x = (RoaringBitmap) super.clone();
                        x.highlowcontainer = highlowcontainer.clone();
                        return x;
                } catch (final CloneNotSupportedException e) {
                        throw new RuntimeException("shouldn't happen with clone",e);
                }
        }

        /**
         * Checks whether the value in included, which is equivalent to checking
         * if the corresponding bit is set (get in BitSet class).
         * 
         * @param x
         *                integer value
         * @return whether the integer value is included.
         */
        public boolean contains(final int x) {
                final short hb = Util.highbits(x);
                final Container C = highlowcontainer.getContainer(hb);
                if (C == null)
                        return false;
                return C.contains(Util.lowbits(x));
        }

        /**
         * Deserialize (retrieve) this bitmap.
         * 
         * The current bitmap is overwritten.
         * 
         * @param in
         *                the DataInput stream
         * @throws IOException
         *                 Signals that an I/O exception has occurred.
         */
        public void deserialize(DataInput in) throws IOException {
        	this.highlowcontainer.deserialize(in);
        }
        
        @Override
        public boolean equals(Object o) {
                if (o instanceof RoaringBitmap) {
                        final RoaringBitmap srb = (RoaringBitmap) o;
                        return srb.highlowcontainer
                                .equals(this.highlowcontainer);
                }
                return false;
        }


        /**
         * Modifies the current bitmap by complementing the bits in the given
         * range, from rangeStart (inclusive) rangeEnd (exclusive).
         * 
         * @param rangeStart
         *                inclusive beginning of range
         * @param rangeEnd
         *                exclusive ending of range
         */
        public void flip(final int rangeStart, final int rangeEnd) {
                if (rangeStart >= rangeEnd)
                        return; // empty range

                final short hbStart = Util.highbits(rangeStart);
                final short lbStart = Util.lowbits(rangeStart);
                final short hbLast = Util.highbits(rangeEnd - 1);
                final short lbLast = Util.lowbits(rangeEnd - 1);

                int max = Util.toIntUnsigned(Util.maxLowBit());
                for (short hb = hbStart; hb <= hbLast; ++hb) {
                        // first container may contain partial range
                        final int containerStart = (hb == hbStart) ? Util
                                .toIntUnsigned(lbStart) : 0;
                        // last container may contain partial range
                        final int containerLast = (hb == hbLast) ? Util
                                .toIntUnsigned(lbLast) : max;
                        final int i = highlowcontainer.getIndex(hb);

                        if (i >= 0) {
                                Container c = highlowcontainer
                                        .getContainerAtIndex(i).inot(
                                                containerStart, containerLast);
                                if (c.getCardinality() > 0)
                                        highlowcontainer.setContainerAtIndex(i,
                                                c);
                                else
                                        highlowcontainer.removeAtIndex(i);
                        } else {
                                highlowcontainer.insertNewKeyValueAt(-i - 1,
                                        hb, Container.rangeOfOnes(
                                                containerStart, containerLast));
                        }
                }
        }

        /**
         * Returns the number of distinct integers added to the bitmap (e.g.,
         * number of bits set).
         * 
         * @return the cardinality
         */
        public int getCardinality() {
                int size = 0;
                for (int i = 0; i < this.highlowcontainer.size(); i++) {
                        size += this.highlowcontainer.getContainerAtIndex(i)
                                .getCardinality();
                }
                return size;
        }

        private IntIterator getIntIterator() {
                return new IntIterator() {
                        int hs = 0;

                        Iterator<Short> iter;

                        short pos = 0;

                        int x;

                        @Override
                        public boolean hasNext() {
                                return pos < RoaringBitmap.this.highlowcontainer
                                        .size();
                        }

                        public IntIterator init() {
                                if (pos < RoaringBitmap.this.highlowcontainer
                                        .size()) {
                                        iter = RoaringBitmap.this.highlowcontainer
                                                .getContainerAtIndex(pos)
                                                .iterator();
                                        hs = Util
                                                .toIntUnsigned(RoaringBitmap.this.highlowcontainer
                                                        .getKeyAtIndex(pos)) << 16;
                                }
                                return this;
                        }

                        @Override
                        public int next() {
                                x = Util.toIntUnsigned(iter.next()) | hs;
                                if (!iter.hasNext()) {
                                        ++pos;
                                        init();
                                }
                                return x;
                        }

                        @Override
                        public void remove() {
                                if ((x & hs) == hs) {// still in same container
                                        iter.remove();
                                } else {
                                        RoaringBitmap.this.remove(x);
                                }
                        }

                }.init();
        }

        /**
         * Estimate of the memory usage of this data structure. This
         * can be expected to be within 1% of the true memory usage.
         * 
         * @return estimated memory usage.
         */
        public int getSizeInBytes() {
                int size = 8;
                for (int i = 0; i < this.highlowcontainer.size(); i++) {
                        final Container c = this.highlowcontainer
                                .getContainerAtIndex(i);
                        size += 2 + c.getSizeInBytes();
                }
                return size;
        }

        @Override
        public int hashCode() {
                return highlowcontainer.hashCode();
        }

        /**
         * iterate over the positions of the true values.
         * 
         * @return the iterator
         */
        @Override
        public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                        int hs = 0;

                        Iterator<Short> iter;

                        short pos = 0;

                        int x;

                        @Override
                        public boolean hasNext() {
                                return pos < RoaringBitmap.this.highlowcontainer
                                        .size();
                        }

                        public Iterator<Integer> init() {
                                if (pos < RoaringBitmap.this.highlowcontainer
                                        .size()) {
                                        iter = RoaringBitmap.this.highlowcontainer
                                                .getContainerAtIndex(pos)
                                                .iterator();
                                        hs = Util
                                                .toIntUnsigned(RoaringBitmap.this.highlowcontainer
                                                        .getKeyAtIndex(pos)) << 16;
                                }
                                return this;
                        }

                        @Override
                        public Integer next() {
                                x = Util.toIntUnsigned(iter.next()) | hs;
                                if (!iter.hasNext()) {
                                        ++pos;
                                        init();
                                }
                                return x;
                        }

                        @Override
                        public void remove() {
                                if ((x & hs) == hs) {// still in same container
                                        iter.remove();
                                } else {
                                        RoaringBitmap.this.remove(x);
                                }
                        }

                }.init();
        }

        /**
         * In-place bitwise OR (union) operation. The current bitmap is
         * modified.
         * 
         * @param x2
         *                other bitmap
         */
        public void or(final RoaringBitmap x2) {
                int pos1 = 0, pos2 = 0;
                int length1 = highlowcontainer.size();
                final int length2 = x2.highlowcontainer.size();
                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);

                        while (true) {
                                if (s1 < s2) {
                                        pos1++;
                                        if (pos1 == length1) {
                                                break main;
                                        }
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        highlowcontainer
                                                .insertNewKeyValueAt(
                                                        pos1,
                                                        s2,
                                                        x2.highlowcontainer
                                                                .getContainerAtIndex(pos2));
                                        pos1++;
                                        length1++;
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        this.highlowcontainer
                                                .setContainerAtIndex(
                                                        pos1,
                                                        highlowcontainer
                                                                .getContainerAtIndex(
                                                                        pos1)
                                                                .ior(x2.highlowcontainer
                                                                        .getContainerAtIndex(pos2)));
                                        pos1++;
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2)) {
                                                break main;
                                        }
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        }
                }
                if (pos1 == length1) {
                        highlowcontainer.appendCopy(x2.highlowcontainer, pos2,
                                length2);
                }
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                ClassNotFoundException {
                this.highlowcontainer.readExternal(in);

        }

        /**
         * If present remove the specified integers (effectively, sets its bit
         * value to false)
         * 
         * @param x
         *                integer value representing the index in a bitmap
         */
        public void remove(final int x) {
                final short hb = Util.highbits(x);
                final int i = highlowcontainer.getIndex(hb);
                if (i < 0)
                        return;
                highlowcontainer.setContainerAtIndex(i, highlowcontainer
                        .getContainerAtIndex(i).remove(Util.lowbits(x)));
                if (highlowcontainer.getContainerAtIndex(i).getCardinality() == 0)
                        highlowcontainer.removeAtIndex(i);
        }

        /**
         * Serialize this bitmap.
         * 
         * The current bitmap is not modified.
         * 
         * @param out
         *                the DataOutput stream
         * @throws IOException
         *                 Signals that an I/O exception has occurred.
         */
        public void serialize(DataOutput out) throws IOException {
        	this.highlowcontainer.serialize(out);
        }

        /**
         * Report the number of bytes required to serialize this bitmap.
         * This is the number of bytes written out when using the serialize
         * method. When using the writeExternal method, the count will be
         * higher due to the overhead of Java serialization.
         * 
         * @return the size in bytes
         */
        public int serializedSizeInBytes() {
                return this.highlowcontainer.serializedSizeInBytes();
        }

        /**
         * Return the set values as an array.
         * 
         * @return array representing the set values.
         */
        public int[] toArray() {
                final int[] array = new int[this.getCardinality()];
                int pos = 0, pos2 = 0;
                while (pos < this.highlowcontainer.size()) {
                        final int hs = Util.toIntUnsigned(this.highlowcontainer
                                .getKeyAtIndex(pos)) << 16;
                        Container c = this.highlowcontainer
                                .getContainerAtIndex(pos++);
                        c.fillLeastSignificant16bits(array, pos2, hs);
                        pos2 += c.getCardinality();
                }
                return array;
        }

        /**
         * A string describing the bitmap.
         * 
         * @return the string
         */
        @Override
        public String toString() {
                final StringBuilder answer = new StringBuilder();
                final IntIterator i = this.getIntIterator();
                answer.append("{");
                if (i.hasNext())
                        answer.append(i.next());
                while (i.hasNext()) {
                        answer.append(",");
                        answer.append(i.next());
                }
                answer.append("}");
                return answer.toString();
        }

        /**
         * Recover allocated but unused memory.
         */
        public void trim() {
                for (int i = 0; i < this.highlowcontainer.size(); i++) {
                        this.highlowcontainer.getContainerAtIndex(i).trim();
                }
        }
        

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
                this.highlowcontainer.writeExternal(out);
        }
        	
        /**
         * In-place bitwise XOR (symmetric difference) operation. The current
         * bitmap is modified.
         * 
         * @param x2
         *                other bitmap
         */
        public void xor(final RoaringBitmap x2) {
                int pos1 = 0, pos2 = 0;
                int length1 = highlowcontainer.size();
                final int length2 = x2.highlowcontainer.size();

                main: if (pos1 < length1 && pos2 < length2) {
                        short s1 = highlowcontainer.getKeyAtIndex(pos1);
                        short s2 = x2.highlowcontainer.getKeyAtIndex(pos2);

                        while (true) {
                                if (s1 < s2) {
                                        pos1++;
                                        if (pos1 == length1) {
                                                break main;
                                        }
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                } else if (s1 > s2) {
                                        highlowcontainer
                                                .insertNewKeyValueAt(
                                                        pos1,
                                                        s2,
                                                        x2.highlowcontainer
                                                                .getContainerAtIndex(pos2));
                                        pos1++;
                                        length1++;
                                        pos2++;
                                        if (pos2 == length2) {
                                                break main;
                                        }
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                } else {
                                        final Container C = highlowcontainer
                                                .getContainerAtIndex(pos1)
                                                .ixor(x2.highlowcontainer
                                                        .getContainerAtIndex(pos2));
                                        if (C.getCardinality() > 0) {
                                                this.highlowcontainer
                                                        .setContainerAtIndex(
                                                                pos1, C);
                                                pos1++;
                                        } else {
                                                highlowcontainer
                                                        .removeAtIndex(pos1);
                                                --length1;
                                        }
                                        pos2++;
                                        if ((pos1 == length1)
                                                || (pos2 == length2)) {
                                                break main;
                                        }
                                        s1 = highlowcontainer
                                                .getKeyAtIndex(pos1);
                                        s2 = x2.highlowcontainer
                                                .getKeyAtIndex(pos2);
                                }
                        }
                }
                if (pos1 == length1) {
                        highlowcontainer.appendCopy(x2.highlowcontainer, pos2,
                                length2);
                }
        }  

}