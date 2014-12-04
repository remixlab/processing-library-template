/*********************************************************************************
 * dandelion_tree
 * Copyright (c) 2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 *
 * All rights reserved. Library that eases the creation of interactive
 * scenes, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 *********************************************************************************/

package remixlab.dandelion.agent;

import remixlab.bias.agent.profile.*;
import remixlab.bias.core.*;
import remixlab.bias.event.*;
import remixlab.dandelion.core.*;
import remixlab.dandelion.core.Constants.*;

/**
 * An abstract {@link remixlab.dandelion.agent.ActionWheeledBiMotionAgent} representing a pointing device supporting 2
 * Degrees-Of-Freedom (e.g., two translations or two rotations), such as most mice.
 */
public class WheeledMouseAgent extends ActionWheeledBiMotionAgent<MotionProfile<DOF2Action>> {
	protected DOF2Event	pressEvent;
	protected DOF2Event	lastEvent;
	protected int				left	= 1, center = 2, right = 3;
	
	boolean						bypassNullEvent, need4Spin, drive, rotateMode;
	float							dFriction;
	InteractiveFrame	iFrame;

	/**
	 * Constructs a PointingAgent. Nothing fancy.
	 * 
	 * @param scn
	 *          AbstractScene
	 * @param n
	 *          Agents name
	 */
	public WheeledMouseAgent(AbstractScene scn, String n) {
		super(new MotionProfile<DOF1Action>(),
				new MotionProfile<DOF1Action>(),
				new MotionProfile<DOF2Action>(),
				new MotionProfile<DOF2Action>(),
				new ClickProfile<ClickAction>(),
				new ClickProfile<ClickAction>(), scn, n);
		pressEvent = new DOF2Event(0, 0);
		lastEvent = new DOF2Event(0, 0);
	}

	@Override
	public DOF2Event feed() {
		return null;
	}

	@Override
	public MotionProfile<DOF2Action> eyeProfile() {
		return camProfile;
	}

	@Override
	public MotionProfile<DOF2Action> frameProfile() {
		return profile;
	}

	/**
	 * Sets the mouse translation sensitivity along X.
	 */
	public void setXTranslationSensitivity(float s) {
		sens[0] = s;
	}

	/**
	 * Sets the mouse translation sensitivity along Y.
	 */
	public void setYTranslationSensitivity(float s) {
		sens[1] = s;
	}

	/**
	 * Return the last event processed by the agent. Internal use, needed by scene visual hints.
	 */
	public DOF2Event lastEvent() {
		return lastEvent;
	}

	/**
	 * Return the last press event processed by the agent. Internal use, needed by scene visual hints.
	 */
	public DOF2Event pressEvent() {
		return pressEvent;
	}

	/**
	 * Same as {@code return buttonModifiersFix(BogusEvent.NOMODIFIER_MASK, button)}.
	 * 
	 * @see #buttonModifiersFix(int, int)
	 */
	public int buttonModifiersFix(int button) {
		return buttonModifiersFix(BogusEvent.NOMODIFIER_MASK, button);
	}

	/**
	 * Hack to deal with some platforms not reporting correctly the mouse event mask, such as with Processing:
	 * https://github.com/processing/processing/issues/1693
	 * <p>
	 * Default implementation simple returns the same mask.
	 */
	public int buttonModifiersFix(int mask, int button) {
		return mask;
	}
	
	// Events
	
	/**
	 * Call {@link #updateTrackedGrabber(BogusEvent)} on the given event.
	 */
	public void move(DOF2Event e) {
		lastEvent = e;
		updateTrackedGrabber(lastEvent);
	}

	/**
	 * Begin interaction and call {@link #handle(BogusEvent)} on the given event. Keeps track of the {@link #pressEvent()}
	 * .
	 */
	public void press(DOF2Event e) {
		lastEvent = e;
		pressEvent = lastEvent.get();
		if (inputGrabber() instanceof InteractiveFrame) {
			if (need4Spin)
				((InteractiveFrame) inputGrabber()).stopSpinning();
			iFrame = (InteractiveFrame) inputGrabber();
			Action<?> a = (inputGrabber() instanceof InteractiveEyeFrame) ? eyeProfile().handle((BogusEvent) lastEvent)
					: frameProfile().handle((BogusEvent) lastEvent);
			if (a == null)
				return;
			DandelionAction dA = (DandelionAction) a.referenceAction();
			if (dA == DandelionAction.SCREEN_TRANSLATE)
				((InteractiveFrame) inputGrabber()).dirIsFixed = false;
			rotateMode = ((dA == DandelionAction.ROTATE) || (dA == DandelionAction.ROTATE_XYZ)
					|| (dA == DandelionAction.ROTATE_CAD)
					|| (dA == DandelionAction.SCREEN_ROTATE) || (dA == DandelionAction.TRANSLATE_XYZ_ROTATE_XYZ));
			if (rotateMode && scene.is3D())
				scene.camera().frame().cadRotationIsReversed = scene.camera().frame()
						.transformOf(scene.camera().frame().sceneUpVector()).y() < 0.0f;
			need4Spin = (rotateMode && (((InteractiveFrame) inputGrabber()).dampingFriction() == 0));
			drive = (dA == DandelionAction.DRIVE);
			bypassNullEvent = (dA == DandelionAction.MOVE_FORWARD) || (dA == DandelionAction.MOVE_BACKWARD)
					|| (drive) && scene.inputHandler().isAgentRegistered(this);
			scene.setZoomVisualHint(dA == DandelionAction.ZOOM_ON_REGION && (inputGrabber() instanceof InteractiveEyeFrame)
					&& scene.inputHandler().isAgentRegistered(this));
			scene.setRotateVisualHint(dA == DandelionAction.SCREEN_ROTATE && (inputGrabber() instanceof InteractiveFrame)
					&& scene.inputHandler().isAgentRegistered(this));
			if (bypassNullEvent || scene.zoomVisualHint() || scene.rotateVisualHint()) {
				if (bypassNullEvent) {
					// This is needed for first person:
					((InteractiveFrame) inputGrabber()).updateSceneUpVector();
					dFriction = ((InteractiveFrame) inputGrabber()).dampingFriction();
					((InteractiveFrame) inputGrabber()).setDampingFriction(0);
					handler.eventTupleQueue().add(new EventGrabberTuple(lastEvent, a, inputGrabber()));
				}
			}
			else
				handle(lastEvent);
		} else
			handle(lastEvent);
	}

	/**
	 * Call {@link #handle(BogusEvent)} on the given event.
	 */
	public void drag(DOF2Event e) {
		lastEvent = e;
		if (!scene.zoomVisualHint()) { // bypass zoom_on_region, may be different when using a touch device :P
			if (drive && inputGrabber() instanceof InteractiveFrame)
				((InteractiveFrame) inputGrabber()).setFlySpeed(0.01f * scene.radius() * 0.01f
						* (lastEvent.y() - pressEvent.y()));
			// never handle ZOOM_ON_REGION on a drag. Could happen if user presses a modifier during drag triggering it
			Action<?> a = (inputGrabber() instanceof InteractiveEyeFrame) ? eyeProfile().handle((BogusEvent) lastEvent)
					: frameProfile().handle((BogusEvent) lastEvent);
			if (a == null)
				return;
			DandelionAction dA = (DandelionAction) a.referenceAction();
			if (dA != DandelionAction.ZOOM_ON_REGION)
				handle(lastEvent);
		}
	}

	/**
	 * Ends interaction and calls {@link #updateTrackedGrabber(BogusEvent)} on the given event.
	 */
	public void release(DOF2Event e) {
		DOF2Event prevEvent = lastEvent().get();
		lastEvent = e;
		if (inputGrabber() instanceof InteractiveFrame)
			// note that the following two lines fail on event when need4Spin
			if (need4Spin && (prevEvent.speed() >= ((InteractiveFrame) inputGrabber()).spinningSensitivity()))
				((InteractiveFrame) inputGrabber()).startSpinning(prevEvent);
		if (scene.zoomVisualHint()) {
			// at first glance this should work
			// handle(event);
			// but the problem is that depending on the order the button and the modifiers are released,
			// different actions maybe triggered, so we go for sure ;) :
			lastEvent.setPreviousEvent(pressEvent);
			enqueueEventTuple(new EventGrabberTuple(lastEvent, DOF2Action.ZOOM_ON_REGION, inputGrabber()));
			scene.setZoomVisualHint(false);
		}
		if (scene.rotateVisualHint())
			scene.setRotateVisualHint(false);
		updateTrackedGrabber(lastEvent);
		if (bypassNullEvent) {
			iFrame.setDampingFriction(dFriction);
			bypassNullEvent = !bypassNullEvent;
		}
		// restore speed after drive action terminates:
		if (drive && inputGrabber() instanceof InteractiveFrame)
			((InteractiveFrame) inputGrabber()).setFlySpeed(0.01f * scene.radius());
	}

	// HIGH-LEVEL

	public void setAsArcball() {
		setAsArcball(false);
	}

	/**
	 * Set mouse bindings as 'arcball'. If {@code trackpad} is true bindings are as follows:
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * No-button -> ROTATE<br>
	 * Shift + No-button -> SCALE<br>
	 * Ctrl + No-button -> TRANSLATE<br>
	 * Center button -> SCREEN_TRANSLATE<br>
	 * Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * No-button -> ROTATE<br>
	 * Shift + No-button -> ZOOM<br>
	 * Ctrl + No-button -> TRANSLATE<br>
	 * Ctrl + Shift + No-button -> ZOOM_ON_REGION<br>
	 * Center button -> SCREEN_TRANSLATE<br>
	 * Right button -> SCREEN_ROTATE.
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * <p>
	 * Note that Alt + No-button is bound to the null action.
	 * <p>
	 * If {@code trackpad} is false bindings are as follows:
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> SCALE<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> ZOOM<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Left button -> ZOOM_ON_REGION<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE.
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setAsFirstPerson()
	 * @see #setAsThirdPerson()
	 */
	public void setAsArcball(boolean trackpad) {
		resetAllProfiles();
		if(trackpad) {
			eyeProfile().setBinding(DOF2Action.ROTATE);
			eyeProfile().setBinding(MotionEvent.SHIFT, MotionEvent.NOBUTTON, scene.is3D() ? DOF2Action.ZOOM : DOF2Action.SCALE);
			eyeProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF2Action.TRANSLATE);
			eyeProfile().setBinding((MotionEvent.CTRL | MotionEvent.SHIFT), MotionEvent.NOBUTTON, DOF2Action.ZOOM_ON_REGION);
			setButtonBinding(Target.EYE, center, DOF2Action.SCREEN_TRANSLATE);
			setButtonBinding(Target.EYE, right, DOF2Action.SCREEN_ROTATE);
			eyeProfile().setBinding(MotionEvent.ALT, MotionEvent.NOBUTTON, null);
			frameProfile().setBinding(DOF2Action.ROTATE);
			frameProfile().setBinding(MotionEvent.SHIFT, MotionEvent.NOBUTTON, DOF2Action.SCALE);
			frameProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF2Action.TRANSLATE);
			setButtonBinding(Target.FRAME, center, DOF2Action.SCREEN_TRANSLATE);
			setButtonBinding(Target.FRAME, right, DOF2Action.SCREEN_ROTATE);
			frameProfile().setBinding(MotionEvent.ALT, MotionEvent.NOBUTTON, null);
		}
		else {
			eyeProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
			eyeProfile().setBinding(buttonModifiersFix(center), center, scene.is3D() ? DOF2Action.ZOOM : DOF2Action.SCALE);
			eyeProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
			eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ZOOM_ON_REGION);
			eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
			eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);
			frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
			frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.SCALE);
			frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);			
		}
		setCommonBindings();
	}
	
	public void setAsFirstPerson() {
		setAsFirstPerson(false);
	}

	/**
	 * Set mouse bindings as 'first-person'. If {@code trackpad} is true bindings are as follows:
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * No-button -> ROTATE<br>
	 * Shift + No-button -> SCALE<br>
	 * Ctrl + No-button -> TRANSLATE<br>
	 * Center button -> SCREEN_TRANSLATE<br>
	 * Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * Ctrl + No-button -> MOVE_FORWARD<br>
	 * No-button -> LOOK_AROUND<br>
	 * Shift + No-button -> MOVE_BACKWARD<br>
	 * Right button -> ROTATE_Z<br>
	 * Ctrl + Shift + No-button -> DRIVE<br>
	 * Ctrl + Shift + Wheel -> ROTATE_Z<br>
	 * Shift + Wheel -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * <p>
	 * Note that Alt + No-button is bound to the null action.
	 * <p>
	 * If {@code trackpad} is false bindings are as follows:
	 * <p>
	 * 1. <b>InteractiveFrame bindings</b><br>
	 * Left button -> ROTATE<br>
	 * Center button -> SCALE<br>
	 * Right button -> TRANSLATE<br>
	 * Shift + Center button -> SCREEN_TRANSLATE<br>
	 * Shift + Right button -> SCREEN_ROTATE<br>
	 * <p>
	 * 2. <b>InteractiveEyeFrame bindings</b><br>
	 * Left button -> MOVE_FORWARD<br>
	 * Center button -> LOOK_AROUND<br>
	 * Right button -> MOVE_BACKWARD<br>
	 * Shift + Left button -> ROTATE_Z<br>
	 * Shift + Center button -> DRIVE<br>
	 * Ctrl + Wheel -> ROLL<br>
	 * Shift + Wheel -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setAsArcball()
	 * @see #setAsThirdPerson()
	 */
	public void setAsFirstPerson(boolean trackpad) {
		resetAllProfiles();
		if(trackpad) {
			eyeProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF2Action.MOVE_FORWARD);
			eyeProfile().setBinding(MotionEvent.SHIFT, MotionEvent.NOBUTTON, DOF2Action.MOVE_BACKWARD);
			eyeProfile().setBinding(MotionEvent.ALT, MotionEvent.NOBUTTON, null);
			setButtonBinding(Target.EYE, right, DOF2Action.ROTATE_Z);
			eyeWheelProfile().setBinding((MotionEvent.CTRL | MotionEvent.SHIFT), DOF1Action.ROTATE_Z);
			if (scene.is3D()) {
				eyeProfile().setBinding(DOF2Action.LOOK_AROUND);
				eyeProfile().setBinding((MotionEvent.CTRL | MotionEvent.SHIFT), MotionEvent.NOBUTTON, DOF2Action.DRIVE);
			}
			frameProfile().setBinding(DOF2Action.ROTATE);
			frameProfile().setBinding(MotionEvent.SHIFT, MotionEvent.NOBUTTON, DOF2Action.SCALE);
			frameProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF2Action.TRANSLATE);
			frameProfile().setBinding(MotionEvent.ALT, MotionEvent.NOBUTTON, null);
			setButtonBinding(Target.FRAME, center, DOF2Action.SCREEN_TRANSLATE);
			setButtonBinding(Target.FRAME, right, DOF2Action.SCREEN_ROTATE);
		}
		else {
			eyeProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.MOVE_FORWARD);
			eyeProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.MOVE_BACKWARD);
			eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ROTATE_Z);
			eyeWheelProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF1Action.ROTATE_Z);
			if (scene.is3D()) {
				eyeProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.LOOK_AROUND);
				eyeProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.DRIVE);
			}
			frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.ROTATE);
			frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.SCALE);
			frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.TRANSLATE);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.SCREEN_TRANSLATE);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, right), right, DOF2Action.SCREEN_ROTATE);
			
		}
		setCommonBindings();
	}
	
	public void setAsThirdPerson() {
		setAsThirdPerson(false);
	}

	/**
	 * Set mouse bindings as 'third-person'. If {@code trackpad} is true bindings are as follows:
	 * <p>
	 * Ctrl + No-button -> MOVE_FORWARD<br>
	 * No-button -> LOOK_AROUND<br>
	 * Shift + No-button -> MOVE_BACKWARD<br>
	 * Ctrl + Shift + Wheel -> ROTATE_Z<br>
	 * Ctrl + Shift + No-button -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * <p>
	 * Note that Alt + No-button is bound to the null action.
	 * <p>
	 * If {@code trackpad} is false bindings are as follows:
	 * <p>
	 * Left button -> MOVE_FORWARD<br>
	 * Center button -> LOOK_AROUND<br>
	 * Right button -> MOVE_BACKWARD<br>
	 * Shift + Left button -> ROLL<br>
	 * Shift + Center button -> DRIVE<br>
	 * <p>
	 * Also set the following (common) bindings are:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * 
	 * @see #setAsArcball()
	 * @see #setAsFirstPerson()
	 */
	public void setAsThirdPerson(boolean trackpad) {
		resetAllProfiles();
		if(trackpad) {
			frameProfile().setBinding(MotionEvent.CTRL, MotionEvent.NOBUTTON, DOF2Action.MOVE_FORWARD);
			frameProfile().setBinding(MotionEvent.SHIFT, MotionEvent.NOBUTTON, DOF2Action.MOVE_BACKWARD);
			frameWheelProfile().setBinding((MotionEvent.CTRL | MotionEvent.SHIFT), DOF1Action.ROTATE_Z);
			frameProfile().setBinding(MotionEvent.ALT, MotionEvent.NOBUTTON, null);
			if (scene.is3D()) {
				frameProfile().setBinding(DOF2Action.LOOK_AROUND);
				frameProfile().setBinding((MotionEvent.CTRL | MotionEvent.SHIFT), MotionEvent.NOBUTTON, DOF2Action.DRIVE);
			}
		}
		else {
			frameProfile().setBinding(buttonModifiersFix(left), left, DOF2Action.MOVE_FORWARD);
			frameProfile().setBinding(buttonModifiersFix(right), right, DOF2Action.MOVE_BACKWARD);
			frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, left), left, DOF2Action.ROTATE_Z);
			if (scene.is3D()) {
				frameProfile().setBinding(buttonModifiersFix(center), center, DOF2Action.LOOK_AROUND);
				frameProfile().setBinding(buttonModifiersFix(MotionEvent.SHIFT, center), center, DOF2Action.DRIVE);
			}
		}
		setCommonBindings();
	}

	/**
	 * Set the following (common) bindings:
	 * <p>
	 * 2 left clicks -> ALIGN_FRAME<br>
	 * 2right clicks -> CENTER_FRAME<br>
	 * Wheel in 2D -> SCALE both, InteractiveFrame and InteractiveEyeFrame<br>
	 * Wheel in 3D -> SCALE InteractiveFrame, and ZOOM InteractiveEyeFrame<br>
	 * <p>
	 * which are used in {@link #setAsArcball()}, {@link #setAsFirstPerson()} and {@link #setAsThirdPerson()}
	 */
	protected void setCommonBindings() {
		eyeClickProfile().setBinding(buttonModifiersFix(left), left, 2, ClickAction.ALIGN_FRAME);
		eyeClickProfile().setBinding(buttonModifiersFix(right), right, 2, ClickAction.CENTER_FRAME);
		frameClickProfile().setBinding(buttonModifiersFix(left), left, 2, ClickAction.ALIGN_FRAME);
		frameClickProfile().setBinding(buttonModifiersFix(right), right, 2, ClickAction.CENTER_FRAME);
		eyeWheelProfile().setBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON,
				scene.is3D() ? DOF1Action.ZOOM : DOF1Action.SCALE);
		frameWheelProfile().setBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON, DOF1Action.SCALE);
	}

	// WRAPPERS
	
	/**
	 * Binds the mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME).
	 */
	public void setBinding(Target target, DOF2Action action) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.setBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON, action);
	}

	/**
	 * Binds the mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME).
	 */
	public void setBinding(Target target, int mask, DOF2Action action) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.setBinding(mask, MotionEvent.NOBUTTON, action);
	}

	/**
	 * Removes the mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeBinding(Target target) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.removeBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON);
	}

	/**
	 * Removes the mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeBinding(Target target, int mask) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.removeBinding(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns {@code true} if the mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasBinding(Target target) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return profile.hasBinding(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns {@code true} if the mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasBinding(Target target, int mask) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return profile.hasBinding(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action action(Target target) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return (DOF2Action) profile.action(MotionEvent.NOMODIFIER_MASK, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action action(Target target, int mask) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return (DOF2Action) profile.action(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Binds the mask-button mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target}
	 * (EYE or FRAME).
	 */
	public void setButtonBinding(Target target, int mask, int button, DOF2Action action) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.setBinding(buttonModifiersFix(mask, button), button, action);
	}

	/**
	 * Binds the button mouse shortcut to the (DOF2) dandelion action to be performed by the given {@code target} (EYE or
	 * FRAME).
	 */
	public void setButtonBinding(Target target, int button, DOF2Action action) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.setBinding(buttonModifiersFix(button), button, action);
	}

	/**
	 * Removes the mask-button mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeButtonBinding(Target target, int mask, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.removeBinding(buttonModifiersFix(mask, button), button);
	}

	/**
	 * Removes the button mouse shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeButtonBinding(Target target, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		profile.removeBinding(buttonModifiersFix(button), button);
	}

	/**
	 * Returns {@code true} if the mask-button mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasButtonBinding(Target target, int mask, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return profile.hasBinding(buttonModifiersFix(mask, button), button);
	}

	/**
	 * Returns {@code true} if the button mouse shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasButtonBinding(Target target, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return profile.hasBinding(buttonModifiersFix(button), button);
	}

	/**
	 * Returns {@code true} if the mouse action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isButtonActionBound(Target target, DOF2Action action) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return profile.isActionBound(action);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mask-button mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action buttonAction(Target target, int mask, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return (DOF2Action) profile.action(buttonModifiersFix(mask, button), button);
	}

	/**
	 * Returns the (DOF2) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given button mouse shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF2Action buttonAction(Target target, int button) {
		MotionProfile<DOF2Action> profile = target == Target.EYE ? eyeProfile() : frameProfile();
		return (DOF2Action) profile.action(buttonModifiersFix(button), button);
	}

	// wheel here

	/**
	 * Binds the mask-wheel shortcut to the (DOF1) dandelion action to be performed by the given {@code target} (EYE or
	 * FRAME).
	 */
	public void setWheelBinding(Target target, int mask, DOF1Action action) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		profile.setBinding(mask, MotionEvent.NOBUTTON, action);
	}

	/**
	 * Binds the wheel to the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME).
	 */
	public void setWheelBinding(Target target, DOF1Action action) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		profile.setBinding(action);
	}

	/**
	 * Removes the mask-wheel shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeWheelBinding(Target target, int mask) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		profile.removeBinding(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Removes the wheel binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeWheelBinding(Target target) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		profile.removeBinding();
	}

	/**
	 * Returns {@code true} if the mask-wheel shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasWheelBinding(Target target, int mask) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		return profile.hasBinding(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns {@code true} if the wheel is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasWheelBinding(Target target) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		return profile.hasBinding();
	}

	/**
	 * Returns {@code true} if the mouse wheel action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isWheelActionBound(Target target, DOF1Action action) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		return profile.isActionBound(action);
	}

	/**
	 * Returns the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given mask-wheel shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF1Action wheelAction(Target target, int mask, DOF1Action action) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		return (DOF1Action) profile.action(mask, MotionEvent.NOBUTTON);
	}

	/**
	 * Returns the (DOF1) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to the
	 * given wheel shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public DOF1Action wheelAction(Target target, DOF1Action action) {
		MotionProfile<DOF1Action> profile = target == Target.EYE ? wheelProfile() : frameWheelProfile();
		return (DOF1Action) profile.action(MotionEvent.NOBUTTON);
	}

	// mouse click

	/**
	 * Binds the mask-button-ncs (number-of-clicks) click-shortcut to the (click) dandelion action to be performed by the
	 * given {@code target} (EYE or FRAME).
	 */
	public void setClickBinding(Target target, int mask, int button, int ncs, ClickAction action) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.setBinding(buttonModifiersFix(mask, button), button, ncs, action);
	}

	/**
	 * Binds the button-ncs (number-of-clicks) click-shortcut to the (click) dandelion action to be performed by the given
	 * {@code target} (EYE or FRAME).
	 */
	public void setClickBinding(Target target, int button, int ncs, ClickAction action) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.setBinding(buttonModifiersFix(button), button, ncs, action);
	}

	/**
	 * Binds the single-clicked button shortcut to the (click) dandelion action to be performed by the given
	 * {@code target} (EYE or FRAME).
	 */
	public void setClickBinding(Target target, int button, ClickAction action) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.setBinding(buttonModifiersFix(button), button, 1, action);
	}

	/**
	 * Removes the mask-button-ncs (number-of-clicks) click-shortcut binding from the
	 * {@link remixlab.dandelion.core.InteractiveEyeFrame} (if {@code eye} is {@code true}) or from the
	 * {@link remixlab.dandelion.core.InteractiveFrame} (if {@code eye} is {@code false}).
	 */
	public void removeClickBinding(Target target, int mask, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.removeBinding(buttonModifiersFix(mask, button), button, ncs);
	}

	/**
	 * Removes the button-ncs (number-of-clicks) click-shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeClickBinding(Target target, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.removeBinding(buttonModifiersFix(button), button, ncs);
	}

	/**
	 * Removes the single-clicked button shortcut binding from the given {@code target} (EYE or FRAME).
	 */
	public void removeClickBinding(Target target, int button) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		profile.removeBinding(buttonModifiersFix(button), button, 1);
	}

	/**
	 * Returns {@code true} if the mask-button-ncs (number-of-clicks) click-shortcut is bound to the given {@code target}
	 * (EYE or FRAME).
	 */
	public boolean hasClickBinding(Target target, int mask, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return profile.hasBinding(buttonModifiersFix(mask, button), button, ncs);
	}

	/**
	 * Returns {@code true} if the button-ncs (number-of-clicks) click-shortcut is bound to the given {@code target} (EYE
	 * or FRAME).
	 */
	public boolean hasClickBinding(Target target, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return profile.hasBinding(buttonModifiersFix(button), button, ncs);
	}

	/**
	 * Returns {@code true} if the single-clicked button shortcut is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean hasClickBinding(Target target, int button) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return profile.hasBinding(buttonModifiersFix(button), button, 1);
	}

	/**
	 * Returns {@code true} if the mouse click action is bound to the given {@code target} (EYE or FRAME).
	 */
	public boolean isClickActionBound(Target target, ClickAction action) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return profile.isActionBound(action);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given mask-button-ncs (number-of-clicks) click-shortcut. Returns {@code null} if no action is bound to the
	 * given shortcut.
	 */
	public ClickAction clickAction(Target target, int mask, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return (ClickAction) profile.action(buttonModifiersFix(mask, button), button, ncs);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given button-ncs (number-of-clicks) click-shortcut. Returns {@code null} if no action is bound to the given
	 * shortcut.
	 */
	public ClickAction clickAction(Target target, int button, int ncs) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return (ClickAction) profile.action(buttonModifiersFix(button), button, ncs);
	}

	/**
	 * Returns the (click) dandelion action to be performed by the given {@code target} (EYE or FRAME) that is bound to
	 * the given single-clicked button shortcut. Returns {@code null} if no action is bound to the given shortcut.
	 */
	public ClickAction clickAction(Target target, int button) {
		ClickProfile<ClickAction> profile = target == Target.EYE ? clickProfile() : frameClickProfile();
		return (ClickAction) profile.action(buttonModifiersFix(button), button, 1);
	}
}