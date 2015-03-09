package com.fourinfo.bloom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.*;

public class BigSet implements Serializable {

	private static final long serialVersionUID = -1402977753086985898L;
	private List<BitSet> bitList = new ArrayList<BitSet>();
	private long maxSize = 2147483647;

	public BigSet(long size) {
		long numSets = size / (long) this.maxSize;
		if (size % (long) this.maxSize != 0) {
			numSets++;
		}
		for (int i = 0; i < numSets; i++) {
			int setSize = (int) Math.min((long) size - (long) i * (long) this.maxSize, (long) this.maxSize);
			BitSet newSet = new BitSet((int) setSize);
			this.bitList.add(newSet);
		}
	}

	public int[] getIndex(long indx) {
		int[] returnValues = new int[2];
		returnValues[0] = (int) (indx / this.maxSize);
		returnValues[1] = (int) (indx % this.maxSize);
		return returnValues;
	}

	public void set(long bitIndex, boolean value) {
		int[] indx = getIndex(bitIndex);
		this.bitList.get(indx[0]).set(indx[1], value);
	}

	public void set(long bitIndex) {
		set(bitIndex, true);
	}

	public boolean get(long bitIndex) {
		int[] indx = getIndex(bitIndex);
		return this.bitList.get(indx[0]).get(indx[1]);
	}

	public void clear() {
		for (BitSet bitSet : this.bitList) {
			bitSet.clear();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final BigSet other = (BigSet) obj;
		return this.bitList.equals(other.bitList);
	}

	@Override
	public int hashCode() {
		return this.bitList.hashCode();
	}

}
