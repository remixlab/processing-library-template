/**************************************************************************************
 * ProScene (version 3.0.0)
 * Copyright (c) 2010-2014 National University of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 * 
 * All rights reserved. Library that eases the creation of interactive scenes
 * in Processing, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 **************************************************************************************/

// Thanks to Sebastian Chaparro, url-PENDING and William Rodriguez, url-PENDING
// for providing an initial picking example and searching the documentation for it:
// http://n.clavaud.free.fr/processing/picking/pickcode.htm
// http://content.gpwiki.org/index.php/OpenGL_Selection_Using_Unique_Color_IDs

package remixlab.proscene;

import java.lang.reflect.Method;

import processing.core.*;
import remixlab.bias.core.*;
import remixlab.bias.event.*;
import remixlab.bias.ext.Profile;
import remixlab.dandelion.core.*;
import remixlab.dandelion.geom.*;
import remixlab.util.*;

/**
 * A {@link remixlab.proscene.Model} {@link remixlab.dandelion.InteractiveFrame.GenericFrame}.
 * 
 * @see remixlab.proscene.Model
 * @see remixlab.dandelion.InteractiveFrame.GenericFrame
 */
public class Model extends InteractiveFrame implements Constants {
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).
				appendSuper(super.hashCode()).
				append(id).
				append(profile).
				toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		Model other = (Model) obj;
		return new EqualsBuilder()
				.appendSuper(super.equals(obj))
				.append(id, other.id)
				.append(profile, other.profile)
				.isEquals();
	}
	
	// shape
	protected PShape	pshape;
	protected int		id;
	protected PVector shift;
	
	// Draw	
	protected Object						drawHandlerObject;
	protected Method						drawHandlerMethod;
		
	// TODO new experimenting with textures	
	protected PImage    tex;	
		
	public Model(Scene scn, PShape ps, PImage texture) {
		super(scn);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		pshape = ps;
		tex = texture;
		shift = new PVector();
		profile = new Profile(this);
	}
	
	//--
	
	//TODO implement high level profile api, according to P5 mouse simplicity behavior 
	
	/**
	 * Constructs a interactive-frame and adds to the {@link remixlab.proscene.Scene#models()} collection.
	 * Calls {@code super(scn}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 * @see #shape()
	 * @see #setShape(PShape)
	 * @see #addGraphicsHandler(Object, String)
	 */
	public Model(Scene scn) {
		super(scn);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		profile = new Profile(this);
		setDefaultBindings();
	}
	
	/**
	 * Constructs an interactive-frame as a child of reference frame, and adds it to the
	 * {@link remixlab.proscene.Scene#models()} collection. Calls {@code super(scn, referenceFrame}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene, Frame)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 * @see #shape()
	 * @see #setShape(PShape)
	 * @see #addGraphicsHandler(Object, String)
	 */
	public Model(Scene scn, Frame referenceFrame) {
		super(scn, referenceFrame);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		profile = new Profile(this);
		setDefaultBindings();
	}
	
	/**
	 * 
	 * Wraps the pshape into this interactive-frame which is then added to the
	 * {@link remixlab.proscene.Scene#models()} collection. Calls {@code super(scn)}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 */
	public Model(Scene scn, PShape ps) {
		super(scn);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		setShape(ps);
		profile = new Profile(this);
		setDefaultBindings();
	}

	/**
	 * Wraps the pshape into this interactive-frame which is created as a child of reference frame and then added to the
	 * {@link remixlab.proscene.Scene#models()} collection. Calls {@code super(scn, referenceFrame)}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene, Frame)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 */
	public Model(Scene scn, Frame referenceFrame, PShape ps) {
		super(scn, referenceFrame);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		setShape(ps);
		profile = new Profile(this);
		setDefaultBindings();
	}

	/**
	 * Wraps the function object procedure into this interactive-frame which is then added it to the
	 * {@link remixlab.proscene.Scene#models()} collection. Calls {@code super(scn}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 * @see #addGraphicsHandler(Object, String)
	 */
	public Model(Scene scn, Object obj, String methodName) {
		super(scn);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		addGraphicsHandler(obj, methodName);
		profile = new Profile(this);
		setDefaultBindings();
	}

	/**
	 * Wraps the the function object procedure into this interactive-frame which is is created as a child of reference frame
	 * and then added to the {@link remixlab.proscene.Scene#models()} collection. Calls {@code super(scn, referenceFrame}.
	 * 
	 * @see remixlab.dandelion.InteractiveFrame.GenericFrame#GenericFrame(GenericScene, Frame)
	 * @see remixlab.proscene.Scene#addModel(Model)
	 * @see #addGraphicsHandler(Object, String)
	 */
	public Model(Scene scn, Frame referenceFrame, Object obj, String methodName) {
		super(scn, referenceFrame);
		((Scene) gScene).addModel(this);
		id = ++Scene.modelCount;
		shift = new PVector();
		addGraphicsHandler(obj, methodName);
		profile = new Profile(this);
		setDefaultBindings();
	}

	protected Model(Model otherFrame) {
		super(otherFrame);
		this.pshape = otherFrame.pshape;
		this.id = otherFrame.id;
		this.shift = otherFrame.shift.copy();
		this.drawHandlerObject = otherFrame.drawHandlerObject;
		this.drawHandlerMethod = otherFrame.drawHandlerMethod;
		this.profile = new Profile(this);
		this.profile.from(otherFrame.profile);
	}
	
	public Profile profile() {
		return profile;
	}

	@Override
	public Model get() {
		return new Model(this);
	}
	
	@Override
	public Model detach() {
		Model frame = new Model((Scene)gScene);
		for(Agent agent : gScene.inputHandler().agents())
			agent.removeGrabber(frame);
		frame.fromFrame(this);
		return frame;
	}
	
	/**
	 * Same as {@code ((Scene) scene).applyTransformation(pg, this)}.
	 * 
	 * @see remixlab.proscene.Scene#applyTransformation(PGraphics, Frame)
	 */
	public void applyTransformation(PGraphics pg) {
		((Scene) gScene).applyTransformation(pg, this);
	}

	/**
	 * Same as {@code ((Scene) scene).applyWorldTransformation(pg, this)}.
	 * 
	 * @see remixlab.proscene.Scene#applyWorldTransformation(PGraphics, Frame)
	 */
	public void applyWorldTransformation(PGraphics pg) {
		((Scene) gScene).applyWorldTransformation(pg, this);
	}

	/**
	 * Internal use. Model color to use in the {@link remixlab.proscene.Scene#pickingBuffer()}.
	 */
	protected int getColor() {
		// see here: http://stackoverflow.com/questions/2262100/rgb-int-to-rgb-python
		return ((Scene) gScene).pickingBuffer().color(id & 255, (id >> 8) & 255, (id >> 16) & 255);
	}
	
	/**
	 * Shifts the {@link #shape()} respect to the frame {@link #position()}. Default value is zero.
	 * 
	 * @see #modelShift()
	 */
	public void shiftModel(PVector shift) {
		this.shift = shift;
	}
	
	/**
	 * Returns the {@link #shape()} shift.
	 * 
	 * @see #shiftModel(PVector)
	 */
	public PVector modelShift() {
		return shift;
	}
	
	// shape

	/**
	 * Returns the shape wrap by this interactive-frame.
	 */
	public PShape shape() {
		return pshape;
	}

	/**
	 * Replaces previous {@link #shape()} with {@code ps}.
	 */
	public void setShape(PShape ps) {
		pshape = ps;
	}
	
	/**
	 * Unsets the shape which is wrapped by this interactive-frame.
	 */
	public PShape unsetShape() {
		PShape prev = pshape;
		pshape = null;
		return prev;
	}
	
	/**
	 * An interactive-frame is selected using <a href="http://schabby.de/picking-opengl-ray-tracing/">'ray-picking'</a>
     * with a color buffer (see {@link remixlab.proscene.Scene#pickingBuffer()}). This method compares the color of 
     * the {@link remixlab.proscene.Scene#pickingBuffer()} at {@code (x,y)} with {@link #getColor()}.
     * Returns true if both colors are the same, and false otherwise.
	 */
	@Override
	public final boolean checkIfGrabsInput(float x, float y) {
		if ((shape() == null  && !this.hasGraphicsHandler() ) || !((Scene) gScene).isPickingBufferEnabled() )
			return super.checkIfGrabsInput(x, y);
		((Scene) gScene).pickingBuffer().pushStyle();
		((Scene) gScene).pickingBuffer().colorMode(PApplet.RGB, 255);
		int index = (int) y * gScene.width() + (int) x;
		if ((0 <= index) && (index < ((Scene) gScene).pickingBuffer().pixels.length))
			return ((Scene) gScene).pickingBuffer().pixels[index] == getColor();
		((Scene) gScene).pickingBuffer().popStyle();
		return false;
	}

	/**
	 * Same as {@code draw(scene.pg())}.
	 * 
	 * @see remixlab.proscene.Scene#drawModels(PGraphics)
	 */
	public void draw() {
		if (shape() == null  && !this.hasGraphicsHandler())
			return;
		PGraphics pg = ((Scene) gScene).pg();
		draw(pg);
	}

	public boolean draw(PGraphics pg) {
		if (shape() == null && !this.hasGraphicsHandler())
			return false;
		pg.pushStyle();
		if (pg == ((Scene) gScene).pickingBuffer()) {
			if(shape()!=null) {
				shape().disableStyle();
				if(tex!=null) shape().noTexture();
			}
			pg.colorMode(PApplet.RGB, 255);
			pg.fill(getColor());
			pg.stroke(getColor());
		}
		pg.pushMatrix();
		((Scene) gScene).applyWorldTransformation(pg, this);
		pg.translate(shift.x, shift.y, shift.z);
		if(shape()!=null)
			pg.shape(shape());
		if( this.hasGraphicsHandler() )
			this.invokeGraphicsHandler(pg);
		pg.popMatrix();
		if (pg == ((Scene) gScene).pickingBuffer()) {
			if(shape()!=null) {
				if(tex!=null)
					shape().texture(tex);
				shape().enableStyle();
			}
		}
		pg.popStyle();
		return true;
	}
	
	// DRAW METHOD REG
	
	protected boolean invokeGraphicsHandler(PGraphics pg) {
		// 3. Draw external registered method
		if (drawHandlerObject != null) {
			try {
				drawHandlerMethod.invoke(drawHandlerObject, new Object[] { pg });
				return true;
			} catch (Exception e) {
				PApplet.println("Something went wrong when invoking your " + drawHandlerMethod.getName() + " method");
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}
	
	/**
	 * Attempt to add a graphics handler method to the Model. The default event handler is a method that
	 * returns void and has one single PGraphics parameter. Note that the method should only deal with geometry and
	 * that not coloring procedure may be specified within it.
	 * 
	 * @param obj
	 *          the object to handle the event
	 * @param methodName
	 *          the method to execute in the object handler class
	 * 
	 * @see #removeGraphicsHandler()
	 * @see #invokeGraphicsHandler(PGraphics)
	 */
	public void addGraphicsHandler(Object obj, String methodName) {
		try {
			drawHandlerMethod = obj.getClass().getMethod(methodName, new Class<?>[] { PGraphics.class });			
			drawHandlerObject = obj;
		} catch (Exception e) {
			PApplet.println("Something went wrong when registering your " + methodName + " method");
			e.printStackTrace();
		}
	}

	/**
	 * Unregisters the graphics handler method (if any has previously been added to the Scene).
	 * 
	 * @see #addGraphicsHandler(Object, String)
	 * @see #invokeGraphicsHandler(PGraphics)
	 */
	public void removeGraphicsHandler() {
		drawHandlerMethod = null;
		drawHandlerObject = null;
	}

	/**
	 * Returns {@code true} if the user has registered a graphics handler method to the Scene and {@code false} otherwise.
	 * 
	 * @see #addGraphicsHandler(Object, String)
	 * @see #invokeGraphicsHandler(PGraphics)
	 */
	public boolean hasGraphicsHandler() {
		if (drawHandlerMethod == null)
			return false;
		return true;
	}
	
	// TODO wheel bindings -> as part of motion bindings
	
	//TODO add me properly
	//TODO decide if it's better to attach the Profile at the IFrame
	public Profile profile;
	
	@Override
	public void performInteraction(BogusEvent event) {
		if( profile.handle(event) )
			return;
	}
	
	//mouse move
	
	public void removeBindings() {
		profile.removeBindings();
	}

	// Motion
	
	public void setMotionBinding(String methodName) {
		profile.setMotionBinding(new MotionShortcut(), methodName);
	}
	
	public void setMotionBinding(Object object, String methodName) {
		profile.setMotionBinding(object, new MotionShortcut(), methodName);
	}
	
	public boolean hasMotionBinding() {
		return profile.hasBinding(new MotionShortcut());
	}
	
	public void removeMotionBinding() {
		profile.removeBinding(new MotionShortcut());
	}
	
	public void setMotionBinding(int id, String methodName) {
		profile.setMotionBinding(new MotionShortcut(id), methodName);
	}
	
	public void setMotionBinding(Object object, int id, String methodName) {
		profile.setMotionBinding(object, new MotionShortcut(id), methodName);
	}
	
	public boolean hasMotionBinding(int it) {
		return profile.hasBinding(new MotionShortcut(id));
	}
	
	public void removeMotionBinding(int id) {
		profile.removeBinding(new MotionShortcut(id));
	}
	
	public void removeMotionBindings() {
		profile.removeMotionBindings();
	}
	
	// Key
	
	public void setKeyBinding(char key, String methodName) {
		profile.setKeyboardBinding(new KeyboardShortcut(KeyAgent.keyCode(key)), methodName);
	}
	
	public void setKeyBinding(Object object, char key, String methodName) {
		profile.setKeyboardBinding(object, new KeyboardShortcut(KeyAgent.keyCode(key)), methodName);
	}
	
	public boolean hasKeyBinding(char key) {
		return profile.hasBinding(new KeyboardShortcut(KeyAgent.keyCode(key)));
	}
	
	public void removeKeyBinding(char key) {
		profile.removeBinding(new KeyboardShortcut(KeyAgent.keyCode(key)));
	}
	
	public void setKeyBinding(int mask, char key, String methodName) {
		profile.setKeyboardBinding(new KeyboardShortcut(mask, KeyAgent.keyCode(key)), methodName);
	}
	
	public void setKeyBinding(Object object, int mask, char key, String methodName) {
		profile.setKeyboardBinding(object, new KeyboardShortcut(mask, KeyAgent.keyCode(key)), methodName);
	}
	
	public boolean hasKeyBinding(int mask, char key) {
		return profile.hasBinding(new KeyboardShortcut(mask, KeyAgent.keyCode(key)));
	}
	
	public void removeKeyBinding(int mask, char key) {
		profile.removeBinding(new KeyboardShortcut(mask, KeyAgent.keyCode(key)));
	}
	
	public void removeKeyBindings() {
		profile.removeKeyboardBindings();
	}
	
	// click
	
	public void setClickBinding(int id, int count, String methodName) {
		profile.setClickBinding(new ClickShortcut(id, count), methodName);
	}
	
	public void setClickBinding(Object object, int id, int count, String methodName) {
		profile.setClickBinding(object, new ClickShortcut(id, count), methodName);
	}
	
	public boolean hasClickBinding(int id, int count) {
		return profile.hasBinding(new ClickShortcut(id, count));
	}
	
	public void removeClickBinding(int id, int count) {
		profile.removeBinding(new ClickShortcut(id, count));
	}
	
	public void removeClickBindings() {
		profile.removeClickBindings();
	}
	
	// TODO click and double click bindings
	
	public void setDefaultBindings() {
		setMotionBinding(LEFT_ID, "gestureArcball");
		setMotionBinding(CENTER_ID, "gestureScale");
		setMotionBinding(RIGHT_ID, "gestureTranslateXY");
		setMotionBinding(WHEEL_ID, "gestureScale");
		
		setClickBinding(LEFT_ID, 2, "gestureAlign");
		setClickBinding(RIGHT_ID, 2, "gestureCenter");
		
		/*
		setKeyBinding('n', "gestureAlign");
		setKeyBinding('c', "gestureCenter");
		setKeyBinding(LEFT_KEY, "gestureTranslateXNeg");
		setKeyBinding(RIGHT_KEY, "gestureTranslateXPos");
		setKeyBinding(DOWN_KEY, KeyboardAction.TRANSLATE_Y_NEG);
		setKeyBinding(UP_KEY, KeyboardAction.TRANSLATE_Y_POS);
		setKeyBinding('z', KeyboardAction.ROTATE_Z_NEG);
		setKeyBinding(BogusEvent.SHIFT, 'z', KeyboardAction.ROTATE_Z_POS);
		*/
	}
}