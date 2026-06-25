
package com.spineviewer.spine.runtime.v31.utils;

import com.badlogic.gdx.utils.Pool;
import com.spineviewer.spine.runtime.v31.Skeleton;
import com.spineviewer.spine.runtime.v31.SkeletonData;

public class SkeletonPool extends Pool<Skeleton> {
	private SkeletonData skeletonData;

	public SkeletonPool (SkeletonData skeletonData) {
		this.skeletonData = skeletonData;
	}

	public SkeletonPool (SkeletonData skeletonData, int initialCapacity) {
		super(initialCapacity);
		this.skeletonData = skeletonData;
	}

	public SkeletonPool (SkeletonData skeletonData, int initialCapacity, int max) {
		super(initialCapacity, max);
		this.skeletonData = skeletonData;
	}

	protected Skeleton newObject () {
		return new Skeleton(skeletonData);
	}
}
