/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.fourinfo.bloom;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;



/*
 * This is a modified version of the bloom filter class originally
 * implemented by Magnus Skjegstad see:
 * 
 * http://github.com/magnuss/java-bloomfilter
 * 
 * The updated were made at 4info Inc. by Narek Amirbekian (Data Scientist)
 * The original class used a java BitSet and integer classes 
 * which have a maximum size of 2^32. This updated version uses
 * Java Longs and a class called BigSet (see BigSet.java) which
 * has the same interfaces of a BitSet but is implemented as many
 * bitSets acting as one paginated BitSet. 
 * 
 * 
 * Original Header from Magnus Skjegstad:
 *
 * Implementation of a Bloom-filter, as described here:
 * http://en.wikipedia.org/wiki/Bloom_filter
 * 
 * For updates and bugfixes, see http://github.com/magnuss/java-bloomfilter
 * 
 * Inspired by the SimpleBloomFilter-class written by Ian Clarke. This
 * implementation provides a more evenly distributed Hash-function by using a
 * proper digest instead of the Java RNG. Many of the changes were proposed in
 * comments in his blog:
 * http://blog.locut.us/2008/01/12/a-decent-stand-alone-java
 * -bloom-filter-implementation/
 * 
 * @param <E>
 *            Object type that is to be inserted into the Bloom filter, e.g.
 *            String or Integer.
 * @author Magnus Skjegstad <magnus@skjegstad.com>
 */
public class BloomFilter<E> implements Serializable {

	private static final long serialVersionUID = -1967459271173541138L;
	private BigSet bigset;
	private long bigSetSize;
	private double bitsPerElement;
	// expected (maximum) number of elements to be added
	private long expectedNumberOfFilterElements;
	// number of elements actually added to the Bloom filter
	private long numberOfAddedElements;
	private int k; // number of hash functions
	// encoding used for storing hash values as strings
	static final Charset charset = Charset.forName("UTF-8");
	// MD5 gives good enough accuracy in most circumstances. Change to SHA1 if
	// it's needed
	static final String hashName = "MD5";
	static final MessageDigest digestFunction;
	static { // The digest method is reused between instances
		MessageDigest tmp;
		try {
			tmp = java.security.MessageDigest.getInstance(hashName);
		} catch (NoSuchAlgorithmException e) {
			tmp = null;
		}
		digestFunction = tmp;
	}

	/**
	 * Constructs an empty Bloom filter. The total length of the Bloom filter
	 * will be c*n.
	 * 
	 * @param c
	 *            is the number of bits used per element.
	 * @param n
	 *            is the expected number of elements the filter will contain.
	 * @param k
	 *            is the number of hash functions used.
	 */
	public BloomFilter(double c, long n, int k) {
		this.expectedNumberOfFilterElements = n;
		this.k = k;
		this.bitsPerElement = c;
		this.bigSetSize = (long) Math.ceil(c * n);
		numberOfAddedElements = 0;
		this.bigset = new BigSet(bigSetSize);
	}

	/**
	 * Constructs an empty Bloom filter. The optimal number of hash functions
	 * (k) is estimated from the total size of the Bloom and the number of
	 * expected elements.
	 * 
	 * @param bigSetSize
	 *            defines how many bits should be used in total for the filter.
	 * @param expectedNumberOElements
	 *            defines the maximum number of elements the filter is expected
	 *            to contain.
	 */
	public BloomFilter(long bigSetSize, long expectedNumberOElements) {
		this(bigSetSize / (double) expectedNumberOElements, expectedNumberOElements, (int) Math.round((bigSetSize / (double) expectedNumberOElements)
				* Math.log(2.0)));
	}

	/**
	 * Constructs an empty Bloom filter with a given false positive probability.
	 * The number of bits per element and the number of hash functions is
	 * estimated to match the false positive probability.
	 * 
	 * @param falsePositiveProbability
	 *            is the desired false positive probability.
	 * @param expectedNumberOfElements
	 *            is the expected number of elements in the Bloom filter.
	 */
	public BloomFilter(double falsePositiveProbability, long expectedNumberOfElements) {
		// c = k / ln(2)
		// k = ceil(-log_2(false prob.))
		this(Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2))) / Math.log(2), expectedNumberOfElements, (int) Math.ceil(-(Math
				.log(falsePositiveProbability) / Math.log(2))));
	}

	/**
	 * Construct a new Bloom filter based on existing Bloom filter data.
	 * 
	 * @param bigSetSize
	 *            defines how many bits should be used for the filter.
	 * @param expectedNumberOfFilterElements
	 *            defines the maximum number of elements the filter is expected
	 *            to contain.
	 * @param actualNumberOfFilterElements
	 *            specifies how many elements have been inserted into the
	 *            <code>filterData</code> BigSet.
	 * @param filterData
	 *            a BigSet representing an existing Bloom filter.
	 */
	public BloomFilter(long bigSetSize, long expectedNumberOfFilterElements, long actualNumberOfFilterElements, BigSet filterData) {
		this(bigSetSize, expectedNumberOfFilterElements);
		this.bigset = filterData;
		this.numberOfAddedElements = actualNumberOfFilterElements;
	}

	/**
	 * Generates a digest based on the contents of a String.
	 * 
	 * @param val
	 *            specifies the input data.
	 * @param charset
	 *            specifies the encoding of the input data.
	 * @return digest as long.
	 */
	public static long createHash(String val, Charset charset) {
		return createHash(val.getBytes(charset));
	}

	/**
	 * Generates a digest based on the contents of a String.
	 * 
	 * @param val
	 *            specifies the input data. The encoding is expected to be
	 *            UTF-8.
	 * @return digest as long.
	 */
	public static long createHash(String val) {
		return createHash(val, charset);
	}

	/**
	 * Generates a digest based on the contents of an array of bytes.
	 * 
	 * @param data
	 *            specifies input data.
	 * @return digest as long.
	 */
	public static long createHash(byte[] data) {
		return createHashes(data, 1)[0];
	}

	/**
	 * Generates digests based on the contents of an array of bytes and splits
	 * the result into 4-byte int's and store them in an array. The digest
	 * function is called until the required number of int's are produced. For
	 * each call to digest a salt is prepended to the data. The salt is
	 * increased by 1 for each call.
	 * 
	 * @param data
	 *            specifies input data.
	 * @param hashes
	 *            number of hashes/int's to produce.
	 * @return array of int-sized hashes
	 */
	public static long[] createHashes(byte[] data, int hashes) {
		long[] result = new long[hashes];

		int k = 0;
		byte salt = 0;
		while (k < hashes) {
			byte[] digest;
			synchronized (digestFunction) {
				digestFunction.update(salt);
				salt++;
				digest = digestFunction.digest(data);
			}

			for (int i = 0; i < digest.length / 8 && k < hashes; i++) {
				long h = 0;
				for (int j = (i * 8); j < (i * 8) + 8; j++) {
					h <<= 8;
					h |= ((long) digest[j]) & 0xFF;
				}
				result[k] = h;
				k++;
			}
		}
		return result;
	}

	/**
	 * Compares the contents of two instances to see if they are equal.
	 * 
	 * @param obj
	 *            is the object to compare to.
	 * @return True if the contents of the objects are equal.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final BloomFilter<E> other = (BloomFilter<E>) obj;
		if (this.expectedNumberOfFilterElements != other.expectedNumberOfFilterElements) {
			return false;
		}
		if (this.k != other.k) {
			return false;
		}
		if (this.bigSetSize != other.bigSetSize) {
			return false;
		}
		if (this.bigset != other.bigset && (this.bigset == null || !this.bigset.equals(other.bigset))) {
			return false;
		}
		return true;
	}

	/**
	 * Calculates a hash code for this class.
	 * 
	 * @return hash code representing the contents of an instance of this class.
	 */
	@Override
	public int hashCode() {
		// TODO fix this hash code
		int hash = 7;
		hash = 61 * hash + (this.bigset != null ? this.bigset.hashCode() : 0);
		hash = 61 * hash + (int) this.expectedNumberOfFilterElements;
		hash = 61 * hash + (int) this.bigSetSize;
		hash = 61 * hash + this.k;
		return hash;
	}

	/**
	 * Calculates the expected probability of false positives based on the
	 * number of expected filter elements and the size of the Bloom filter. <br />
	 * <br />
	 * The value returned by this method is the <i>expected</i> rate of false
	 * positives, assuming the number of inserted elements equals the number of
	 * expected elements. If the number of elements in the Bloom filter is less
	 * than the expected value, the true probability of false positives will be
	 * lower.
	 * 
	 * @return expected probability of false positives.
	 */
	public double expectedFalsePositiveProbability() {
		return getFalsePositiveProbability(expectedNumberOfFilterElements);
	}

	/**
	 * Calculate the probability of a false positive given the specified number
	 * of inserted elements.
	 * 
	 * @param numberOfElements
	 *            number of inserted elements.
	 * @return probability of a false positive.
	 */
	public double getFalsePositiveProbability(double numberOfElements) {
		// (1 - e^(-k * n / m)) ^ k
		return Math.pow((1 - Math.exp(-k * (double) numberOfElements / (double) bigSetSize)), k);

	}

	/**
	 * Get the current probability of a false positive. The probability is
	 * calculated from the size of the Bloom filter and the current number of
	 * elements added to it.
	 * 
	 * @return probability of false positives.
	 */
	public double getFalsePositiveProbability() {
		return getFalsePositiveProbability(numberOfAddedElements);
	}

	/**
	 * Returns the value chosen for K.<br />
	 * <br />
	 * K is the optimal number of hash functions based on the size of the Bloom
	 * filter and the expected number of inserted elements.
	 * 
	 * @return optimal k.
	 */
	public int getK() {
		return k;
	}

	/**
	 * Sets all bits to false in the Bloom filter.
	 */
	public void clear() {
		bigset.clear();
		numberOfAddedElements = 0;
	}

	/**
	 * Adds an object to the Bloom filter. The output from the object's
	 * toString() method is used as input to the hash functions.
	 * 
	 * @param element
	 *            is an element to register in the Bloom filter.
	 */
	public void add(E element) {
		add(element.toString().getBytes(charset));
	}

	/**
	 * Adds an array of bytes to the Bloom filter.
	 * 
	 * @param bytes
	 *            array of bytes to add to the Bloom filter.
	 */
	public void add(byte[] bytes) {
		long[] hashes = createHashes(bytes, k);
		for (long hash : hashes)
			bigset.set(Math.abs(hash % bigSetSize), true);
		numberOfAddedElements++;
	}

	/**
	 * Adds all elements from a Collection to the Bloom filter.
	 * 
	 * @param c
	 *            Collection of elements.
	 */
	public void addAll(Collection<? extends E> c) {
		for (E element : c)
			add(element);
	}

	/**
	 * Returns true if the element could have been inserted into the Bloom
	 * filter. Use getFalsePositiveProbability() to calculate the probability of
	 * this being correct.
	 * 
	 * @param element
	 *            element to check.
	 * @return true if the element could have been inserted into the Bloom
	 *         filter.
	 */
	public boolean contains(E element) {
		return contains(element.toString().getBytes(charset));
	}

	/**
	 * Returns true if the array of bytes could have been inserted into the
	 * Bloom filter. Use getFalsePositiveProbability() to calculate the
	 * probability of this being correct.
	 * 
	 * @param bytes
	 *            array of bytes to check.
	 * @return true if the array could have been inserted into the Bloom filter.
	 */
	public boolean contains(byte[] bytes) {
		long[] hashes = createHashes(bytes, k);
		for (long hash : hashes) {
			if (!bigset.get(Math.abs(hash % bigSetSize))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true if all the elements of a Collection could have been inserted
	 * into the Bloom filter. Use getFalsePositiveProbability() to calculate the
	 * probability of this being correct.
	 * 
	 * @param c
	 *            elements to check.
	 * @return true if all the elements in c could have been inserted into the
	 *         Bloom filter.
	 */
	public boolean containsAll(Collection<? extends E> c) {
		for (E element : c)
			if (!contains(element))
				return false;
		return true;
	}

	/**
	 * Read a single bit from the Bloom filter.
	 * 
	 * @param bit
	 *            the bit to read.
	 * @return true if the bit is set, false if it is not.
	 */
	public boolean getBit(long bit) {
		return bigset.get(bit);
	}

	/**
	 * Set a single bit in the Bloom filter.
	 * 
	 * @param bit
	 *            is the bit to set.
	 * @param value
	 *            If true, the bit is set. If false, the bit is cleared.
	 */
	public void setBit(long bit, boolean value) {
		bigset.set(bit, value);
	}

	/**
	 * Return the bit set used to store the Bloom filter.
	 * 
	 * @return bit set representing the Bloom filter.
	 */
	public BigSet getBigSet() {
		return bigset;
	}

	/**
	 * Returns the number of bits in the Bloom filter. Use count() to retrieve
	 * the number of inserted elements.
	 * 
	 * @return the size of the bigset used by the Bloom filter.
	 */
	public long size() {
		return this.bigSetSize;
	}

	/**
	 * Returns the number of elements added to the Bloom filter after it was
	 * constructed or after clear() was called.
	 * 
	 * @return number of elements added to the Bloom filter.
	 */
	public long count() {
		return this.numberOfAddedElements;
	}

	/**
	 * Returns the expected number of elements to be inserted into the filter.
	 * This value is the same value as the one passed to the constructor.
	 * 
	 * @return expected number of elements.
	 */
	public long getExpectedNumberOfElements() {
		return expectedNumberOfFilterElements;
	}

	/**
	 * Get expected number of bits per element when the Bloom filter is full.
	 * This value is set by the constructor when the Bloom filter is created.
	 * See also getBitsPerElement().
	 * 
	 * @return expected number of bits per element.
	 */
	public double getExpectedBitsPerElement() {
		return this.bitsPerElement;
	}

	/**
	 * Get actual number of bits per element based on the number of elements
	 * that have currently been inserted and the length of the Bloom filter. See
	 * also getExpectedBitsPerElement().
	 * 
	 * @return number of bits per element.
	 */
	public double getBitsPerElement() {
		return this.bigSetSize / (double) numberOfAddedElements;
	}
}
