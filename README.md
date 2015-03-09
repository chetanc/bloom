# bloom
Bloom Filter which can support over a billion elements

Written by Narek Amirbekian at 4info Inc.

This is a modified version of the bloom filter class originally implemented by Magnus Skjegstad see:

http://github.com/magnuss/java-bloomfilter

The original class used a java BitSet and integer classes which have a maximum size of 2^32. This updated version uses Java Longs and a class called BigSet (see BigSet.java) which has the same interfaces of a BitSet but is implemented as many bitSets acting as one paginated BitSet.
