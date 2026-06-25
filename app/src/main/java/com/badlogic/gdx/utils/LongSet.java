/******************************************************************************
 * Spine Runtimes License Agreement
 * Last updated April 5, 2025. Replaces all prior versions.
 *
 * Copyright (c) 2013-2025, Esoteric Software LLC
 *
 * Integration of the Spine Runtimes into software or otherwise creating
 * derivative works of the Spine Runtimes is permitted under the terms and
 * conditions of Section 2 of the Spine Editor License Agreement:
 * http://esotericsoftware.com/spine-editor-license
 *
 * Otherwise, it is permitted to integrate the Spine Runtimes into software
 * or otherwise create derivative works of the Spine Runtimes (collectively,
 * "Products"), provided that each user of the Products must obtain their own
 * Spine Editor license and redistribution of the Products in any form must
 * include this license and copyright notice.
 *
 * THE SPINE RUNTIMES ARE PROVIDED BY ESOTERIC SOFTWARE LLC "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL ESOTERIC SOFTWARE LLC BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES,
 * BUSINESS INTERRUPTION, OR LOSS OF USE, DATA, OR PROFITS) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THE SPINE RUNTIMES, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *****************************************************************************/

package com.badlogic.gdx.utils;

/** A set of primitive long values backed by a {@link LongMap}.
 * <p>
 * This class provides efficient storage and retrieval of unique long values. */
public class LongSet {
	private final LongMap<Boolean> map;

	/** Creates a new empty LongSet. */
	public LongSet () {
		map = new LongMap<>();
	}

	/** Creates a new empty LongSet with the specified initial capacity. */
	public LongSet (int initialCapacity) {
		map = new LongMap<>(initialCapacity);
	}

	/** Clears the set and ensures it can hold the specified number of items without resizing. */
	public void clear (int capacity) {
		map.clear(capacity);
	}

	/** Adds all elements from the specified array to this set.
	 * @return true if this set was modified (any element was not already present). */
	public boolean addAll (long[] array) {
		boolean modified = false;
		for (int i = 0, n = array.length; i < n; i++) {
			long key = array[i];
			Boolean existing = map.get(key);
			if (existing == null) {
				map.put(key, Boolean.TRUE);
				modified = true;
			}
		}
		return modified;
	}

	/** Returns true if this set contains the specified value. */
	public boolean contains (long value) {
		return map.containsKey(value);
	}
}
