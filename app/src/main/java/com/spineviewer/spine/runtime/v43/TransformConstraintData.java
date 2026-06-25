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

package com.spineviewer.spine.runtime.v43;

import static com.spineviewer.spine.runtime.v43.utils.SpineUtils.*;

import com.badlogic.gdx.utils.Array;

/** Stores the setup pose for a {@link TransformConstraint}.
 * <p>
 * See <a href="https://esotericsoftware.com/spine-transform-constraints">Transform constraints</a> in the Spine User Guide. */
public class TransformConstraintData extends ConstraintData<TransformConstraint, TransformConstraintPose> {
	static public final int ROTATION = 0, X = 1, Y = 2, SCALEX = 3, SCALEY = 4, SHEARY = 5;

	final Array<BoneData> bones = new Array(true, 0, BoneData.class);
	BoneData source;
	float[] offsets = new float[6];
	boolean localSource, localTarget, additive, clamp;
	final Array<FromProperty> properties = new Array(true, 1, FromProperty.class);

	public TransformConstraintData (String name) {
		super(name, new TransformConstraintPose());
	}

	public TransformConstraint create (Skeleton skeleton) {
		return new TransformConstraint(this, skeleton);
	}

	/** The bones that will be modified by this transform constraint. */
	public Array<BoneData> getBones () {
		return bones;
	}

	/** The bone whose world transform will be copied to the constrained bones. */
	public BoneData getSource () {
		return source;
	}

	public void setSource (BoneData source) {
		if (source == null) throw new IllegalArgumentException("source cannot be null.");
		this.source = source;
	}

	/** An offset added to the constrained bone rotation. */
	public float getOffsetRotation () {
		return offsets[ROTATION];
	}

	public void setOffsetRotation (float offsetRotation) {
		offsets[ROTATION] = offsetRotation;
	}

	/** An offset added to the constrained bone X translation. */
	public float getOffsetX () {
		return offsets[X];
	}

	public void setOffsetX (float offsetX) {
		offsets[X] = offsetX;
	}

	/** An offset added to the constrained bone Y translation. */
	public float getOffsetY () {
		return offsets[Y];
	}

	public void setOffsetY (float offsetY) {
		offsets[Y] = offsetY;
	}

	/** An offset added to the constrained bone scaleX. */
	public float getOffsetScaleX () {
		return offsets[SCALEX];
	}

	public void setOffsetScaleX (float offsetScaleX) {
		offsets[SCALEX] = offsetScaleX;
	}

	/** An offset added to the constrained bone scaleY. */
	public float getOffsetScaleY () {
		return offsets[SCALEY];
	}

	public void setOffsetScaleY (float offsetScaleY) {
		offsets[SCALEY] = offsetScaleY;
	}

	/** An offset added to the constrained bone shearY. */
	public float getOffsetShearY () {
		return offsets[SHEARY];
	}

	public void setOffsetShearY (float offsetShearY) {
		offsets[SHEARY] = offsetShearY;
	}

	/** Reads the source bone's local transform instead of its world transform. */
	public boolean getLocalSource () {
		return localSource;
	}

	public void setLocalSource (boolean localSource) {
		this.localSource = localSource;
	}

	/** Sets the constrained bones' local transforms instead of their world transforms. */
	public boolean getLocalTarget () {
		return localTarget;
	}

	public void setLocalTarget (boolean localTarget) {
		this.localTarget = localTarget;
	}

	/** Adds the source bone transform to the constrained bones instead of setting it absolutely. */
	public boolean getAdditive () {
		return additive;
	}

	public void setAdditive (boolean additive) {
		this.additive = additive;
	}

	/** Prevents constrained bones from exceeding the ranged defined by {@link ToProperty#offset} and {@link ToProperty#max}. */
	public boolean getClamp () {
		return clamp;
	}

	public void setClamp (boolean clamp) {
		this.clamp = clamp;
	}

	/** The mapping of transform properties to other transform properties. */
	public Array<FromProperty> getProperties () {
		return properties;
	}

	/** Source property for a {@link TransformConstraint}. */
	static abstract public class FromProperty {
		/** The value of this property that corresponds to {@link ToProperty#offset}. */
		public float offset;

		/** Constrained properties. */
		public final Array<ToProperty> to = new Array(true, 1, ToProperty.class);

		/** Reads this property from the specified bone. */
		abstract public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets);
	}

	/** Constrained property for a {@link TransformConstraint}. */
	static abstract public class ToProperty {
		/** The value of this property that corresponds to {@link FromProperty#offset}. */
		public float offset;

		/** The maximum value of this property when {@link TransformConstraintData#clamp clamped}. */
		public float max;

		/** The scale of the {@link FromProperty} value in relation to this property. */
		public float scale;

		/** Reads the mix for this property from the specified pose. */
		abstract public float mix (TransformConstraintPose pose);

		/** Applies the value to this property. */
		abstract public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive);
	}

	static public class FromRotate extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			if (local) return source.rotation + offsets[ROTATION];
			float sx = skeleton.scaleX, sy = skeleton.scaleY;
			float value = atan2(source.c / sy, source.a / sx) * radDeg
				+ ((source.a * source.d - source.b * source.c) * sx * sy > 0 ? offsets[ROTATION] : -offsets[ROTATION]);
			if (value < 0) value += 360;
			return value;
		}
	}

	static public class ToRotate extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixRotate;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local)
				bone.rotation += (additive ? value : value - bone.rotation) * pose.mixRotate;
			else {
				float sx = skeleton.scaleX, sy = skeleton.scaleY, ix = 1 / sx, iy = 1 / sy;
				float a = bone.a * ix, b = bone.b * ix, c = bone.c * iy, d = bone.d * iy;
				value *= degRad;
				if (!additive) value -= atan2(c, a);
				if (value > PI)
					value -= PI2;
				else if (value < -PI) //
					value += PI2;
				value *= pose.mixRotate;
				float cos = cos(value), sin = sin(value);
				bone.a = (cos * a - sin * c) * sx;
				bone.b = (cos * b - sin * d) * sx;
				bone.c = (sin * a + cos * c) * sy;
				bone.d = (sin * b + cos * d) * sy;
			}
		}
	}

	static public class FromX extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			return local ? source.x + offsets[X] : (offsets[X] * source.a + offsets[Y] * source.b + source.worldX) / skeleton.scaleX;
		}
	}

	static public class ToX extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixX;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local)
				bone.x += (additive ? value : value - bone.x) * pose.mixX;
			else {
				if (!additive) value -= bone.worldX / skeleton.scaleX;
				bone.worldX += value * pose.mixX * skeleton.scaleX;
			}
		}
	}

	static public class FromY extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			return local ? source.y + offsets[Y] : (offsets[X] * source.c + offsets[Y] * source.d + source.worldY) / skeleton.scaleY;
		}
	}

	static public class ToY extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixY;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local)
				bone.y += (additive ? value : value - bone.y) * pose.mixY;
			else {
				if (!additive) value -= bone.worldY / skeleton.scaleY;
				bone.worldY += value * pose.mixY * skeleton.scaleY;
			}
		}
	}

	static public class FromScaleX extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			if (local) return source.scaleX + offsets[SCALEX];
			float a = source.a / skeleton.scaleX, c = source.c / skeleton.scaleY;
			return (float)Math.sqrt(a * a + c * c) + offsets[SCALEX];
		}
	}

	static public class ToScaleX extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixScaleX;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local) {
				if (additive)
					bone.scaleX *= 1 + (value - 1) * pose.mixScaleX;
				else if (bone.scaleX != 0) //
					bone.scaleX += (value - bone.scaleX) * pose.mixScaleX;
			} else if (additive) {
				float s = 1 + (value - 1) * pose.mixScaleX;
				bone.a *= s;
				bone.c *= s;
			} else {
				float a = bone.a / skeleton.scaleX, c = bone.c / skeleton.scaleY, s = (float)Math.sqrt(a * a + c * c);
				if (s != 0) {
					s = 1 + (value - s) * pose.mixScaleX / s;
					bone.a *= s;
					bone.c *= s;
				}
			}
		}
	}

	static public class FromScaleY extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			if (local) return source.scaleY + offsets[SCALEY];
			float b = source.b / skeleton.scaleX, d = source.d / skeleton.scaleY;
			return (float)Math.sqrt(b * b + d * d) + offsets[SCALEY];
		}
	}

	static public class ToScaleY extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixScaleY;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local) {
				if (additive)
					bone.scaleY *= 1 + (value - 1) * pose.mixScaleY;
				else if (bone.scaleY != 0) //
					bone.scaleY += (value - bone.scaleY) * pose.mixScaleY;
			} else if (additive) {
				float s = 1 + (value - 1) * pose.mixScaleY;
				bone.b *= s;
				bone.d *= s;
			} else {
				float b = bone.b / skeleton.scaleX, d = bone.d / skeleton.scaleY, s = (float)Math.sqrt(b * b + d * d);
				if (s != 0) {
					s = 1 + (value - s) * pose.mixScaleY / s;
					bone.b *= s;
					bone.d *= s;
				}
			}
		}
	}

	static public class FromShearY extends FromProperty {
		public float value (Skeleton skeleton, BonePose source, boolean local, float[] offsets) {
			if (local) return source.shearY + offsets[SHEARY];
			float ix = 1 / skeleton.scaleX, iy = 1 / skeleton.scaleY;
			return (atan2(source.d * iy, source.b * ix) - atan2(source.c * iy, source.a * ix)) * radDeg - 90 + offsets[SHEARY];
		}
	}

	static public class ToShearY extends ToProperty {
		public float mix (TransformConstraintPose pose) {
			return pose.mixShearY;
		}

		public void apply (Skeleton skeleton, TransformConstraintPose pose, BonePose bone, float value, boolean local,
			boolean additive) {
			if (local) {
				if (!additive) value -= bone.shearY;
				bone.shearY += value * pose.mixShearY;
			} else {
				float sx = skeleton.scaleX, sy = skeleton.scaleY, b = bone.b / sx, d = bone.d / sy, by = atan2(d, b);
				value = (value + 90) * degRad;
				if (additive)
					value -= PI / 2;
				else {
					value -= by - atan2(bone.c / sy, bone.a / sx);
					if (value > PI)
						value -= PI2;
					else if (value < -PI) //
						value += PI2;
				}
				value = by + value * pose.mixShearY;
				float s = (float)Math.sqrt(b * b + d * d);
				bone.b = cos(value) * s * sx;
				bone.d = sin(value) * s * sy;
			}
		}
	}
}
