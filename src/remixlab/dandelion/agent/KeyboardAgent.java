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

import remixlab.bias.branch.*;
import remixlab.bias.branch.profile.*;
import remixlab.bias.core.*;
import remixlab.bias.event.*;
import remixlab.bias.event.shortcut.*;
import remixlab.dandelion.core.*;
import remixlab.dandelion.core.Constants.*;

public class KeyboardAgent extends Agent {
	protected AbstractScene																											scene;
	protected Branch<GlobalAction, Profile<Shortcut, SceneAction>>	keySceneBranch;
	protected Branch<MotionAction, Profile<Shortcut, KeyboardAction>>	keyFrameBranch, keyEyeBranch;

	public KeyboardAgent(AbstractScene scn, String n) {
		super(scn.inputHandler(), n);
		scene = scn;
		keySceneBranch = new Branch<GlobalAction, Profile<Shortcut, SceneAction>>(
				new Profile<Shortcut, SceneAction>(),
				this,
				"scene_keyboard_branch");
		keyFrameBranch = new Branch<MotionAction, Profile<Shortcut, KeyboardAction>>(new Profile<Shortcut, KeyboardAction>(),
				this,
				"frame_keyboard_branch");
		keyEyeBranch = new Branch<MotionAction, Profile<Shortcut, KeyboardAction>>(new Profile<Shortcut, KeyboardAction>(),
				this,
				"eye_keyboard_branch");
		// new, mimics eye -> motionAgent -> scene -> keyAgent
		// addGrabber(scene);
		// addGrabber(scene.eye().frame());
		resetDefaultGrabber();
		setDefaultGrabber(scene);
		setDefaultBindings();
	}

  @Override
  public boolean addGrabber(Grabber frame) {
  	if(frame instanceof AbstractScene)
  		return addGrabber(scene, keySceneBranch);
  	if (frame instanceof InteractiveFrame)
  		return addGrabber((InteractiveFrame) frame,	((InteractiveFrame) frame).isEyeFrame() ? keyEyeBranch : keyFrameBranch);
  	if (!(frame instanceof InteractiveGrabber))
  		return super.addGrabber(frame);
  	return false;
  }

	/*
	// TODO debug
	@Override
	public boolean addGrabber(Grabber frame) {
		if (frame instanceof AbstractScene)
			return addGrabber(scene, keySceneBranch);
		if (frame instanceof InteractiveFrame) {
			if (((InteractiveFrame) frame).isEyeFrame())
				System.out.println("adding EYE frame in keyboard");
			else
				System.out.println("adding FRAME frame in keyboard");
			return addGrabber((InteractiveFrame) frame, ((InteractiveFrame) frame).isEyeFrame() ? keyEyeBranch
					: keyFrameBranch);
		}
		if (!(frame instanceof InteractiveGrabber))
			return super.addGrabber(frame);
		return false;
	}
	*/

	/**
	 * Returns the scene this object belongs to
	 */
	public AbstractScene scene() {
		return scene;
	}

	@Override
	public void resetDefaultGrabber() {
		addGrabber(scene);
		setDefaultGrabber(scene);
	}

	public Branch<GlobalAction, Profile<Shortcut, SceneAction>> sceneBranch() {
		return keySceneBranch;
	}

	public Branch<MotionAction, Profile<Shortcut, KeyboardAction>> eyeBranch() {
		return keyEyeBranch;
	}

	public Branch<MotionAction, Profile<Shortcut, KeyboardAction>> frameBranch() {
		return keyFrameBranch;
	}

	@Override
	public KeyboardEvent feed() {
		return null;
	}

	protected Profile<Shortcut, SceneAction> sceneProfile() {
		return sceneBranch().profile();
	}

	protected Profile<Shortcut, KeyboardAction> eyeProfile() {
		return eyeBranch().profile();
	}

	protected Profile<Shortcut, KeyboardAction> frameProfile() {
		return frameBranch().profile();
	}

	protected Profile<Shortcut, KeyboardAction> motionProfile(Target target) {
		return target == Target.EYE ? eyeProfile() : frameProfile();
	}

	/**
	 * Set the default keyboard shortcuts as follows:
	 * <p>
	 * {@code 'a' -> KeyboardAction.TOGGLE_AXIS_VISUAL_HINT}<br>
	 * {@code 'f' -> KeyboardAction.TOGGLE_FRAME_VISUAL_HINT}<br>
	 * {@code 'g' -> KeyboardAction.TOGGLE_GRID_VISUAL_HINT}<br>
	 * {@code 'm' -> KeyboardAction.TOGGLE_ANIMATION}<br>
	 * {@code 'e' -> KeyboardAction.TOGGLE_CAMERA_TYPE}<br>
	 * {@code 'h' -> KeyboardAction.DISPLAY_INFO}<br>
	 * {@code 'r' -> KeyboardAction.TOGGLE_PATHS_VISUAL_HINT}<br>
	 * {@code 's' -> KeyboardAction.INTERPOLATE_TO_FIT}<br>
	 * {@code 'S' -> KeyboardAction.SHOW_ALL}<br>
	 * <p>
	 * {@code left_arrow -> KeyboardAction.MOVE_LEFT}<br>
	 * {@code right_arrow -> KeyboardAction.MOVE_RIGHT}<br>
	 * {@code up_arrow -> KeyboardAction.MOVE_UP}<br>
	 * {@code down_arrow -> KeyboardAction.MOVE_DOWN	}<br>
	 * {@code CTRL + java.awt.event.KeyEvent.VK_1 -> KeyboardAction.ADD_KEYFRAME_TO_PATH_1}<br>
	 * {@code ALT + java.awt.event.KeyEvent.VK_1 -> KeyboardAction.DELETE_PATH_1}<br>
	 * {@code CTRL + java.awt.event.KeyEvent.VK_2 -> KeyboardAction.ADD_KEYFRAME_TO_PATH_2}<br>
	 * {@code ALT + java.awt.event.KeyEvent.VK_2 -> KeyboardAction.DELETE_PATH_2}<br>
	 * {@code CTRL + java.awt.event.KeyEvent.VK_3 -> KeyboardAction.ADD_KEYFRAME_TO_PATH_3}<br>
	 * {@code ALT + java.awt.event.KeyEvent.VK_3 -> KeyboardAction.DELETE_PATH_3}<br>
	 * <p>
	 * Finally, it calls: {@code setKeyCodeToPlayPath(java.awt.event.KeyEvent.VK_1, 1)},
	 * {@code setKeyCodeToPlayPath(java.awt.event.KeyEvent.VK_2, 2)} and
	 * {@code setKeyCodeToPlayPath(java.awt.event.KeyEvent.VK_3, 3)} to play the paths.
	 * 
	 * @see remixlab.dandelion.agent.KeyboardAgent#setDefaultBindings()
	 * @see remixlab.dandelion.agent.KeyboardAgent#setKeyCodeToPlayPath(int, int)
	 */
	public void setDefaultBindings() {
		//TODO docs pending
		removeBindings();
		removeBindings(Target.EYE);
		removeBindings(Target.FRAME);
		// 1. Scene bindings
	  // VK_A : 65
		setBinding(65, SceneAction.TOGGLE_AXES_VISUAL_HINT);
	  // VK_E : 69
		setBinding(69, SceneAction.TOGGLE_CAMERA_TYPE);
	  // VK_F : 70
		setBinding(70, SceneAction.TOGGLE_PICKING_VISUAL_HINT);
	  // VK_G : 71
		setBinding(71, SceneAction.TOGGLE_GRID_VISUAL_HINT);
	  // VK_H : 72
		setBinding(72, SceneAction.DISPLAY_INFO);		
	  // VK_M : 77
		setBinding(77, SceneAction.TOGGLE_ANIMATION);		
	  // VK_R : 82
		setBinding(82, SceneAction.TOGGLE_PATHS_VISUAL_HINT);
	  // VK_R : 83
		setBinding(83, SceneAction.INTERPOLATE_TO_FIT);
		//setBinding('S', SceneAction.SHOW_ALL);
		
		// VK_LEFT : 37
		setBinding(BogusEvent.NO_MODIFIER_MASK, 37, SceneAction.MOVE_LEFT);
		// VK_UP : 38
		setBinding(BogusEvent.NO_MODIFIER_MASK, 38, SceneAction.MOVE_UP);
		// VK_RIGHT : 39
		setBinding(BogusEvent.NO_MODIFIER_MASK, 39, SceneAction.MOVE_RIGHT);
		// VK_DOWN : 40
		setBinding(BogusEvent.NO_MODIFIER_MASK, 40, SceneAction.MOVE_DOWN);

		// VK_1 : 49
		setBinding(BogusEvent.CTRL, 49, SceneAction.ADD_KEYFRAME_TO_PATH_1);
		setBinding(BogusEvent.ALT, 49, SceneAction.DELETE_PATH_1);
		setKeyCodeToPlayPath(49, 1);
		// VK_2 : 50
		setBinding(BogusEvent.CTRL, 50, SceneAction.ADD_KEYFRAME_TO_PATH_2);
		setBinding(BogusEvent.ALT, 50, SceneAction.DELETE_PATH_2);
		setKeyCodeToPlayPath(50, 2);
		// VK_3 : 51
		setBinding(BogusEvent.CTRL, 51, SceneAction.ADD_KEYFRAME_TO_PATH_3);
		setBinding(BogusEvent.ALT, 51, SceneAction.DELETE_PATH_3);
		setKeyCodeToPlayPath(51, 3);
		
		// 2. Eye bindings
	  // VK_A : 65
		setBinding(Target.EYE, 65, KeyboardAction.ALIGN_FRAME);
		// VK_C
		setBinding(Target.EYE, 67, KeyboardAction.CENTER_FRAME);
		// VK_X
		setBinding(Target.EYE, 88, KeyboardAction.TRANSLATE_DOWN_X);
	  // VK_X
		setBinding(Target.EYE, BogusEvent.SHIFT, 88, KeyboardAction.TRANSLATE_UP_X);
	  // VK_Y
		setBinding(Target.EYE, 89, KeyboardAction.TRANSLATE_DOWN_Y);
	  // VK_Y
		setBinding(Target.EYE, BogusEvent.SHIFT, 89, KeyboardAction.TRANSLATE_UP_Y);
	  // VK_Z
		setBinding(Target.EYE, 90, KeyboardAction.ROTATE_DOWN_Z);
	  // VK_Z
		setBinding(Target.EYE, BogusEvent.SHIFT, 90, KeyboardAction.ROTATE_UP_Z);
		
		// 3. Frame bindings
		// VK_A : 65
		setBinding(Target.FRAME, 65, KeyboardAction.ALIGN_FRAME);
		// VK_C
		setBinding(Target.FRAME, 67, KeyboardAction.CENTER_FRAME);
		// VK_X
		setBinding(Target.FRAME, 88, KeyboardAction.TRANSLATE_DOWN_X);
	  // VK_X
		setBinding(Target.FRAME, BogusEvent.SHIFT, 88, KeyboardAction.TRANSLATE_UP_X);
	  // VK_Y
		setBinding(Target.FRAME, 89, KeyboardAction.TRANSLATE_DOWN_Y);
	  // VK_Y
		setBinding(Target.FRAME, BogusEvent.SHIFT, 89, KeyboardAction.TRANSLATE_UP_Y);
	  // VK_Z
		setBinding(Target.FRAME, 90, KeyboardAction.ROTATE_DOWN_Z);
	  // VK_Z
		setBinding(Target.FRAME, BogusEvent.SHIFT, 90, KeyboardAction.ROTATE_UP_Z);
	}

	/**
	 * Sets the default (virtual) key to play eye paths.
	 */
	public void setKeyCodeToPlayPath(int vkey, int path) {
		switch (path) {
		case 1:
			setBinding(BogusEvent.NO_MODIFIER_MASK, vkey, SceneAction.PLAY_PATH_1);
			break;
		case 2:
			setBinding(BogusEvent.NO_MODIFIER_MASK, vkey, SceneAction.PLAY_PATH_2);
			break;
		case 3:
			setBinding(BogusEvent.NO_MODIFIER_MASK, vkey, SceneAction.PLAY_PATH_3);
			break;
		default:
			break;
		}
	}
	
  //high level
	//i. scene
	
	public void setBinding(Shortcut shortcut, SceneAction action) {
		sceneProfile().setBinding(shortcut, action);
	}
	
	public void removeBinding(Shortcut shortcut) {
		sceneProfile().removeBinding(shortcut);
	}
	
  public boolean hasBinding(Shortcut shortcut) {
  	return sceneProfile().hasBinding(shortcut);
	}
  
  public SceneAction action(Shortcut shortcut) {
		return sceneProfile().action(shortcut);
	}
  
  //don't override from here
  
  /**
	 * Removes all shortcut bindings.
	 */
	public void removeBindings() {
		sceneProfile().removeBindings();
	}
  
  /**
	 * Returns {@code true} if the keyboard action is bound.
	 */
	public boolean isActionBound(SceneAction action) {
		return sceneProfile().isActionBound(action);
	}
	
	//ii. Frame
	
	public void setBinding(Target target, Shortcut shortcut, KeyboardAction action) {
		motionProfile(target).setBinding(shortcut, action);
	}
	
	public void removeBinding(Target target, Shortcut shortcut) {
		motionProfile(target).removeBinding(shortcut);
	}
	
  public boolean hasBinding(Target target, Shortcut shortcut) {
  	return motionProfile(target).hasBinding(shortcut);
	}
  
  public KeyboardAction action(Target target, Shortcut shortcut) {
		return motionProfile(target).action(shortcut);
	}
  
  //don't override from here
  
  /**
	 * Removes all shortcut bindings.
	 */
	public void removeBindings(Target target) {
		motionProfile(target).removeBindings();
	}
  
  /**
	 * Returns {@code true} if the keyboard action is bound.
	 */
	public boolean isActionBound(Target target, KeyboardAction action) {
		return motionProfile(target).isActionBound(action);
	}
	
	//end

	/**
	 * Binds the key shortcut to the (Keyboard) dandelion action.
	 */
	//TODO pending
	/*
	public void setBinding(Character key, GlobalAction action) {
		sceneProfile().setBinding(key, action);
	}
	*/
	
	/**
	 * Binds the vKey (virtual key) shortcut to the (Keyboard) dandelion action.
	 */
	public void setBinding(int vKey, SceneAction action) {
		setBinding(new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey), action);
	}

	/**
	 * Binds the mask-vKey (virtual key) shortcut to the (Keyboard) dandelion action.
	 */
	public void setBinding(int mask, int vKey, SceneAction action) {
		setBinding(new Shortcut(mask, vKey), action);
	}

	/**
	 * Removes key shortcut binding (if present).
	 */
  //TODO pending
	/*
	public void removeBinding(Character key) {
		sceneProfile().removeBinding(key);
	}
	*/
	
	/**
	 * Removes mask-vKey (virtual key) shortcut binding (if present).
	 */
	public void removeBinding(int vKey) {
		removeBinding(new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Removes mask-vKey (virtual key) shortcut binding (if present).
	 */
	public void removeBinding(int mask, int vKey) {
		removeBinding(new Shortcut(mask, vKey));
	}

	/**
	 * Returns {@code true} if the key shortcut is bound to a (Keyboard) dandelion action.
	 */
  //TODO pending
	/*
	public boolean hasBinding(Character key) {
		return sceneProfile().hasBinding(key);
	}
	*/
	
	/**
	 * Returns {@code true} if the vKey (virtual key) shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasBinding(int vKey) {
		return hasBinding(new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Returns {@code true} if the mask-vKey (virtual key) shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasBinding(int mask, int vKey) {
		return hasBinding(new Shortcut(mask, vKey));
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given key shortcut. Returns {@code null} if no action
	 * is bound to the given shortcut.
	 */
	//TODO pending
	/*
	public GlobalAction action(Character key) {
		return action(new Shortcut(key));
	}
	*/
	
	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given vKey (virtual key) shortcut. Returns
	 * {@code null} if no action is bound to the given shortcut.
	 */
	public SceneAction action(int vKey) {
		return action(new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given mask-vKey (virtual key) shortcut. Returns
	 * {@code null} if no action is bound to the given shortcut.
	 */
	public SceneAction action(int mask, int vKey) {
		return action(new Shortcut(mask, vKey));
	}

	// FRAMEs
	// TODO DOCs are broken (since they were copied/pasted from above)

	/**
	 * Binds the key shortcut to the (Keyboard) dandelion action.
	 */
  //TODO pending
	/*
	public void setBinding(Target target, Character key, KeyboardAction action) {
		motionProfile(target).setBinding(key, action);
	}
	*/
	
	/**
	 * Binds the vKey (virtual key) shortcut to the (Keyboard) dandelion action.
	 */
	public void setBinding(Target target, int vKey, KeyboardAction action) {
		setBinding(target, new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey), action);
	}

	/**
	 * Binds the mask-vKey (virtual key) shortcut to the (Keyboard) dandelion action.
	 */
	public void setBinding(Target target, int mask, int vKey, KeyboardAction action) {
		setBinding(target, new Shortcut(mask, vKey), action);
	}

	/**
	 * Removes key shortcut binding (if present).
	 */
  //TODO pending
	/*
	public void removeBinding(Target target, Character key) {
		motionProfile(target).removeBinding(key);
	}
	*/
	
	/**
	 * Removes vKey (virtual key) shortcut binding (if present).
	 */
	public void removeBinding(Target target, int vKey) {
		removeBinding(target, new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Removes mask-vKey (virtual key) shortcut binding (if present).
	 */
	public void removeBinding(Target target, int mask, int vKey) {
		removeBinding(target, new Shortcut(mask, vKey));
	}

	/**
	 * Returns {@code true} if the key shortcut is bound to a (Keyboard) dandelion action.
	 */
  //TODO pending
	/*
	public boolean hasBinding(Target target, Character key) {
		return motionProfile(target).hasBinding(key);
	}
	*/
	
	/**
	 * Returns {@code true} if the vKey (virtual key) shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasBinding(Target target, int vKey) {
		return hasBinding(target, new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Returns {@code true} if the mask-vKey (virtual key) shortcut is bound to a (Keyboard) dandelion action.
	 */
	public boolean hasBinding(Target target, int mask, int vKey) {
		return hasBinding(target, new Shortcut(mask, vKey));
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given key shortcut. Returns {@code null} if no action
	 * is bound to the given shortcut.
	 */
  //TODO pending
	/*
	public KeyboardAction action(Target target, Character key) {
		return action(target, new Shortcut(key));
	}
	*/
	
	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given vKey (virtual key) shortcut. Returns
	 * {@code null} if no action is bound to the given shortcut.
	 */
	public KeyboardAction action(Target target, int vKey) {
		return action(target, new Shortcut(BogusEvent.NO_MODIFIER_MASK, vKey));
	}

	/**
	 * Returns the (Keyboard) dandelion action that is bound to the given mask-vKey (virtual key) shortcut. Returns
	 * {@code null} if no action is bound to the given shortcut.
	 */
	public KeyboardAction action(Target target, int mask, int vKey) {
		return action(target, new Shortcut(mask, vKey));
	}
}