/*******************************************************************************
 * ProScene (version 1.9.50)
 * Copyright (c) 2013 National university of Colombia, https://github.com/remixlab
 * @author Jean Pierre Charalambos, http://otrolado.info/
 * 
 * All rights reserved. Library that eases the creation of interactive scenes
 * in Processing, released under the terms of the GNU Public License v3.0
 * which is available at http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package remixlab.proscene;

import processing.core.*;
import processing.event.*;
import processing.opengl.*;
import remixlab.dandelion.agent.*;
import remixlab.dandelion.core.*;
import remixlab.dandelion.geom.*;
import remixlab.dandelion.renderer.*;
import remixlab.fpstiming.*;
import remixlab.tersehandling.generic.event.*;
import remixlab.tersehandling.core.*;
import remixlab.tersehandling.generic.profile.*;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A 3D interactive Processing scene.
 * <p>
 * A Scene has a full reach Camera, it can be used for on-screen or off-screen
 * rendering purposes (see the different constructors), and it has two means to
 * manipulate objects: an {@link #interactiveFrame()} single instance (which by
 * default is null) and a {@link #trackedGrabber()} pool.
 * <h3>Usage</h3>
 * To use a Scene you have three choices:
 * <ol>
 * <li><b>Direct instantiation</b>. In this case you should instantiate your own
 * Scene object at the {@code PApplet.setup()} function.
 * See the example <i>BasicUse</i>.
 * <li><b>Inheritance</b>. In this case, once you declare a Scene derived class,
 * you should implement {@link #proscenium()} which defines the objects in your
 * scene. Just make sure to define the {@code PApplet.draw()} method, even if
 * it's empty. See the example <i>AlternativeUse</i>.
 * <li><b>External draw handler registration</b>. You can even declare an
 * external drawing method and then register it at the Scene with
 * {@link #addDrawHandler(Object, String)}. That method should return {@code
 * void} and have one single {@code Scene} parameter. This strategy may be useful
 * when there are multiple viewers sharing the same drawing code. See the
 * example <i>StandardCamera</i>.
 * </ol>
 * <h3>Interactivity mechanisms</h3>
 * Proscene provides two interactivity mechanisms to manage your scene: global
 * keyboard shortcuts and camera profiles.
 * <ol>
 * <li><b>Global keyboard shortcuts</b> provide global configuration options
 * such as {@link #drawGrid()} or {@link #drawAxis()} that are common among
 * the different registered camera profiles. To define a global keyboard shortcut use
 * {@link #setShortcut(Character, KeyboardAction)} or one of its different forms.
 * Check {@link #setDefaultBindings()} to see the default global keyboard shortcuts.
 * <li><b>Camera profiles</b> represent a set of camera keyboard shortcuts, and camera and
 * frame mouse bindings which together represent a "camera mode". The scene provide
 * high-level methods to manage camera profiles such as
 * {@link #registerCameraProfile(CameraProfile)},
 * {@link #unregisterCameraProfile(CameraProfile)} or {@link #currentCameraProfile()}
 * among others. To perform the configuration of a camera profile see the CameraProfile
 * class documentation.
 * </ol>
 * <h3>Animation mechanisms</h3>
 * Proscene provides three animation mechanisms to define how your scene evolves
 * over time:
 * <ol>
 * <li><b>Overriding the {@link #animate()} method.</b>  In this case, once you
 * declare a Scene derived class, you should implement {@link #animate()} which
 * defines how your scene objects evolve over time. See the example <i>Animation</i>.
 * <li><b>External animation handler registration.</b> You can also declare an
 * external animation method and then register it at the Scene with
 * {@link #addAnimationHandler(Object, String)}. That method should return {@code
 * void} and have one single {@code Scene} parameter. See the example
 * <i>AnimationHandler</i>.
 * <li><b>By querying the state of the {@link #animatedFrameWasTriggered} variable.</b>
 * During the drawing loop, the variable {@link #animatedFrameWasTriggered} is set
 * to {@code true} each time an animated frame is triggered (and to {@code false}
 * otherwise), which is useful to notify the outside world when an animation event
 * occurs. See the example <i>Flock</i>.
 */
public class Scene extends AbstractScene /**implements PConstants*/ {
	public static Vec toVec(PVector v) {
		return new Vec(v.x,v.y,v.z);
	}
	
	public static PVector toPVector(Vec v) {
		return new PVector(v.x(),v.y(),v.z());
	}
	
	public static Mat toMat(PMatrix3D m) {
		return new Mat(m.get(new float[16]), true);
	}
	
  public static PMatrix3D toPMatrix(Mat m) {
  	float[] a = m.getTransposed(new float[16]);
		return new PMatrix3D(a[0]  ,a[1]  ,a[2]  ,a[3],
												 a[4]  ,a[5]  ,a[6]  ,a[7],
												 a[8]  ,a[9]  ,a[10] ,a[11],
												 a[12] ,a[13] ,a[14] ,a[15]);
	}
	
	public class ProsceneKeyboard extends KeyboardAgent {
		public ProsceneKeyboard(Scene scn, String n) {
			super(scn, n);
		}
		
		public void keyEvent(KeyEvent e) {
			GenericKeyboardEvent<KeyboardAction> event = new GenericKeyboardEvent<KeyboardAction>( e.getModifiers(), e.getKey(), e.getKeyCode() );
			if(e.getAction() == KeyEvent.TYPE)
				handleKey(event);
			else
				if(e.getAction() == KeyEvent.RELEASE)
					handle(event);
		}
	}
	
	public class ProsceneMouse extends MouseAgent {
		Scene scene;
		boolean bypassNullEvent, zoomOnRegion, screenRotate, need4Spin;
		Point fCorner = new Point();
		Point lCorner = new Point();
		GenericDOF2Event<DOF2Action> event, prevEvent;
		float dFriction = viewport().frame().dampingFriction();
		InteractiveFrame iFrame;
		
		public ProsceneMouse(Scene scn, String n) {
			super(scn, n);
			scene = scn;
			
			//cameraProfile().setBinding(TH_META, TH_RIGHT, DOF2Action.TRANSLATE);	
			
			/**
			//TODO testing:
			cameraClickProfile().setClickBinding((TH_SHIFT | TH_CTRL ), TH_RIGHT, 1, ClickAction.INTERPOLATE_TO_FIT);
			cameraClickProfile().setClickBinding(TH_NOMODIFIER_MASK, TH_CENTER, 1, ClickAction.DRAW_AXIS);
			frameClickProfile().setClickBinding(TH_NOMODIFIER_MASK, TH_CENTER, 1, ClickAction.DRAW_GRID);		
			cameraClickProfile().setClickBinding(TH_SHIFT, TH_RIGHT, 1, ClickAction.PLAY_PATH_1);
			*/
		}
		
		public void mouseEvent(processing.event.MouseEvent e) {
			if( e.getAction() == processing.event.MouseEvent.MOVE ) {
				event = new GenericDOF2Event<DOF2Action>(prevEvent, e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY());
				updateGrabber(event);
				prevEvent = event.get();
			}
			if( e.getAction() == processing.event.MouseEvent.PRESS ) {
				  event = new GenericDOF2Event<DOF2Action>(prevEvent, e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY(), e.getModifiers(), e.getButton());
					if(grabber() instanceof InteractiveFrame) {
						if( need4Spin )	((InteractiveFrame)grabber()).stopSpinning();
						iFrame = (InteractiveFrame)grabber();
						Actionable<?> a = (grabber() instanceof InteractiveCameraFrame) ? cameraProfile().handle((Duoable<?>)event) : frameProfile().handle((Duoable<?>)event);
						if(a==null) return;
						DandelionAction dA = (DandelionAction) a.referenceAction();
						if( dA == DandelionAction.SCREEN_TRANSLATE ) ((InteractiveFrame)grabber()).dirIsFixed = false;						
						need4Spin = ( ((dA == DandelionAction.ROTATE) || (dA == DandelionAction.ROTATE3) || (dA == DandelionAction.SCREEN_ROTATE) || (dA == DandelionAction.TRANSLATE_ROTATE) ) && (((InteractiveFrame) grabber()).dampingFriction() == 0));
						bypassNullEvent = (dA == DandelionAction.MOVE_FORWARD) || (dA == DandelionAction.MOVE_BACKWARD) || (dA == DandelionAction.DRIVE) && scene.isDefaultMouseAgentInUse();
						zoomOnRegion = dA == DandelionAction.ZOOM_ON_REGION && (grabber() instanceof InteractiveCameraFrame) && scene.isDefaultMouseAgentInUse();
						screenRotate = dA == DandelionAction.SCREEN_ROTATE && (grabber() instanceof InteractiveCameraFrame) && scene.isDefaultMouseAgentInUse();
						if(bypassNullEvent || zoomOnRegion || screenRotate) {
							if(bypassNullEvent) {
								//TODO: experimental, this is needed for first person:
								((InteractiveFrame)grabber()).updateFlyUpVector();
								dFriction = ((InteractiveFrame)grabber()).dampingFriction();
								((InteractiveFrame)grabber()).setDampingFriction(0);
								handler.eventTupleQueue().add(new EventGrabberDuobleTuple(event, a, grabber()));	
							}
							if(zoomOnRegion || screenRotate) {
								lCorner.set(e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY());
								if(zoomOnRegion)
									fCorner.set(e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY());
							}
					  }
					  else
					  	handle(event);		
					} else
						handle(event);
					prevEvent = event.get();	  
			}			
			if( e.getAction() == processing.event.MouseEvent.DRAG ) {
				//if( e.getAction() == processing.event.MouseEvent.MOVE ) {//e.g., rotate without dragging any button also possible :P
				if(zoomOnRegion || screenRotate)
					lCorner.set(e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY());
				if( ! zoomOnRegion ) { //bypass zoom_on_region, may be different when using a touch device :P
					event = new GenericDOF2Event<DOF2Action>(prevEvent, e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY(), e.getModifiers(), e.getButton());
					handle(event);
				  prevEvent = event.get();
				}
			}			
			if( e.getAction() == processing.event.MouseEvent.RELEASE ) {
				if( grabber() instanceof InteractiveFrame ) if(need4Spin && (prevEvent.speed() >= ((InteractiveFrame) grabber()).spinningSensitivity()))	
					((InteractiveFrame) grabber()).startSpinning(prevEvent);
				event = new GenericDOF2Event<DOF2Action>(prevEvent, e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY(), e.getModifiers(), e.getButton());
				if(zoomOnRegion) {
					//at first glance this should work
					//handle(event);
					//but the problem is that depending on the order the button and the modifiers are released,
					//different actions maybe triggered, so we go for sure ;) :
					enqueueEventTuple(new EventGrabberDuobleTuple(event, DOF2Action.ZOOM_ON_REGION, grabber()));
					zoomOnRegion = false;
				}
				if(screenRotate) screenRotate = false;
				updateGrabber(event);
				prevEvent = event.get();
				if(bypassNullEvent) {	
					iFrame.setDampingFriction(dFriction);
					bypassNullEvent = !bypassNullEvent;
				}				
			}
			if( e.getAction() == processing.event.MouseEvent.WHEEL ) {				
				handle(new GenericDOF1Event<WheelAction>(e.getCount(), e.getModifiers(), TH_NOBUTTON));
			}			
			if( e.getAction() == MouseEvent.CLICK ) {
				handle(new GenericClickEvent<ClickAction>(e.getX() - scene.upperLeftCorner.getX(), e.getY() - scene.upperLeftCorner.getY(), e.getModifiers(), e.getButton(), e.getCount()));
			}	
		}
		
		//hack to deal with this: https://github.com/processing/processing/issues/1693
		//is to override all the following so that:
		//1. Whenever TH_CENTER appears TH_ALT should be present
		//2. Whenever TH_RIGHT appears TH_META should be present
		@Override
		public void setAsFirstPerson() {		
			cameraProfile().setBinding(TH_NOMODIFIER_MASK, TH_LEFT, DOF2Action.MOVE_FORWARD);
			cameraProfile().setBinding(TH_ALT, TH_CENTER, DOF2Action.LOOK_AROUND);
			cameraProfile().setBinding(TH_META, TH_RIGHT, DOF2Action.MOVE_BACKWARD);
			cameraProfile().setBinding(TH_SHIFT, TH_LEFT, DOF2Action.ROLL);
			cameraProfile().setBinding((TH_ALT | TH_SHIFT), TH_CENTER, DOF2Action.DRIVE);
			cameraWheelProfile().setBinding(TH_CTRL, TH_NOBUTTON, WheelAction.ROLL);
			cameraWheelProfile().setBinding(TH_SHIFT, TH_NOBUTTON, WheelAction.DRIVE);
		}
		
		@Override
		public void setAsThirdPerson() {
			frameProfile().setBinding(TH_NOMODIFIER_MASK, TH_LEFT, DOF2Action.MOVE_FORWARD);
	    frameProfile().setBinding(TH_ALT, TH_CENTER, DOF2Action.LOOK_AROUND);
	    frameProfile().setBinding(TH_META, TH_RIGHT, DOF2Action.MOVE_BACKWARD);
	    frameProfile().setBinding(TH_SHIFT, TH_LEFT, DOF2Action.ROLL);
			frameProfile().setBinding((TH_ALT | TH_SHIFT), TH_CENTER, DOF2Action.DRIVE);
		}
		
		@Override
		public void setAsArcball() {
			cameraProfile().setBinding(TH_NOMODIFIER_MASK, TH_LEFT, DOF2Action.ROTATE);
			cameraProfile().setBinding(TH_ALT, TH_CENTER, DOF2Action.ZOOM);
			cameraProfile().setBinding(TH_META, TH_RIGHT, DOF2Action.TRANSLATE);		
			cameraProfile().setBinding(TH_SHIFT, TH_LEFT, DOF2Action.ZOOM_ON_REGION);
			cameraProfile().setBinding((TH_ALT | TH_SHIFT), TH_CENTER, DOF2Action.SCREEN_TRANSLATE);
			cameraProfile().setBinding((TH_META | TH_SHIFT), TH_RIGHT, DOF2Action.SCREEN_ROTATE);
				
			frameProfile().setBinding(TH_NOMODIFIER_MASK, TH_LEFT, DOF2Action.ROTATE);
			frameProfile().setBinding(TH_ALT, TH_CENTER, DOF2Action.ZOOM);
			frameProfile().setBinding(TH_META, TH_RIGHT, DOF2Action.TRANSLATE);
			frameProfile().setBinding((TH_ALT | TH_SHIFT), TH_CENTER, DOF2Action.SCREEN_TRANSLATE);
			frameProfile().setBinding((TH_META | TH_SHIFT), TH_RIGHT, DOF2Action.SCREEN_ROTATE);
		}
	}
	
	protected class TimerWrap implements Timable {
		Scene scene;
		Timer timer;
		TimerTask timerTask;
		Taskable caller;
		boolean runOnlyOnce;
		long prd;

		public TimerWrap(Scene scn, Taskable o) {
			this(scn, o, false);
		}

		public TimerWrap(Scene scn, Taskable o, boolean singleShot) {
			scene = scn;
			runOnlyOnce = singleShot;
			caller = o;
		}

		public Taskable timerJob() {
			return caller;
		}

		@Override
		public void create() {
			stop();
			timer = new Timer();
			timerTask = new TimerTask() {
				public void run() {
					caller.execute();
				}
			};
		}

		@Override
		public void run(long period) {
			prd = period;
			run();
		}

		@Override
		public void run() {
			create();
			if(isSingleShot())
				timer.schedule(timerTask, prd);
			else
				timer.scheduleAtFixedRate(timerTask, 0, prd);		
		}

		@Override
		public void cancel() {
			stop();
		}

		@Override
		public void stop() {
			if (timer != null) {
				timer.cancel();
				timer.purge();
				/**
				 * prd = 0; runOnlyOnce = false;
				 */
			}
		}

		@Override
		public boolean isActive() {
			return timer != null;
		}

		@Override
		public long period() {
			return prd;
		}

		@Override
		public void setPeriod(long period) {
			prd = period;
		}

		@Override
		public boolean isSingleShot() {
			return runOnlyOnce;
		}

		@Override
		public void setSingleShot(boolean singleShot) {
			runOnlyOnce = singleShot;
		}
	}
	
	protected class P5Drawing2D implements Depictable, PConstants {
		protected Scene scene;
		Mat proj;

		public P5Drawing2D(Scene scn) {
			scene = scn;
			proj = new Mat();
		}
		
		public Scene scene() {
			return scene;
		}
		
		public boolean isRightHanded() {
			return scene.isRightHanded();
		}
		
		public boolean isLeftHanded() {
			return scene.isLeftHanded();
		}
		
		public PGraphics pg() {
			return scene.pg();
		}	
		
		@Override
		public void drawAxis(float length) {
			final float charWidth = length / 40.0f;
			final float charHeight = length / 30.0f;
			final float charShift = 1.05f * length;
			
	    pg().pushStyle();		
	    pg().strokeWeight(2);
			pg().beginShape(LINES);	
			
			// The X		
			pg().stroke(200, 0, 0);		
			pg().vertex(charShift + charWidth, -charHeight);
			pg().vertex(charShift - charWidth, charHeight);
			pg().vertex(charShift - charWidth, -charHeight);
			pg().vertex(charShift + charWidth, charHeight);
			
			// The Y
			pg().stroke(0, 200, 0);
			pg().vertex(charWidth, charShift + charHeight);
			pg().vertex(0.0f, charShift + 0.0f);
			pg().vertex(-charWidth, charShift + charHeight);
			pg().vertex(0.0f, charShift + 0.0f);
			pg().vertex(0.0f, charShift + 0.0f);
			pg().vertex(0.0f, charShift + -charHeight);
			
			pg().endShape();		
			pg().popStyle();		
			
			pg().pushStyle();				
			pg().strokeWeight(2);			  
			
		  // X Axis
			pg().stroke(200, 0, 0);
			pg().line(0, 0, length, 0);
		  // Y Axis
			pg().stroke(0, 200, 0);		
			pg().line(0, 0, 0, length);		

			pg().popStyle();
		}

		public void drawGrid(float size, int nbSubdivisions) {
			pg().pushStyle();
			pg().stroke(170, 170, 170);
			pg().strokeWeight(1);
			pg().beginShape(LINES);
			for (int i = 0; i <= nbSubdivisions; ++i) {
				final float pos = size * (2.0f * i / nbSubdivisions - 1.0f);
				pg().vertex(pos, -size);
				pg().vertex(pos, +size);
				pg().vertex(-size, pos);
				pg().vertex(size, pos);
			}
			pg().endShape();
			pg().popStyle();
		}

		@Override
		public void drawDottedGrid(float size, int nbSubdivisions) {
			float posi, posj;
			pg().pushStyle();
			pg().stroke(170);
			pg().strokeWeight(2);
			pg().beginShape(POINTS);
			for (int i = 0; i <= nbSubdivisions; ++i) {
				posi = size * (2.0f * i / nbSubdivisions - 1.0f);
				for(int j = 0; j <= nbSubdivisions; ++j) {
					posj = size * (2.0f * j / nbSubdivisions - 1.0f);
					pg().vertex(posi, posj);
				}
			}
			pg().endShape();
			//pg().popStyle();
			
			int internalSub = 5;
			int subSubdivisions = nbSubdivisions * internalSub;
			//pg().pushStyle();
			pg().stroke(100);
			pg().strokeWeight(1);
			pg().beginShape(POINTS);
			for (int i = 0; i <= subSubdivisions; ++i) {
				posi = size * (2.0f * i / subSubdivisions - 1.0f);
				for(int j = 0; j <= subSubdivisions; ++j) {
					posj = size * (2.0f * j / subSubdivisions - 1.0f);
					if(( (i%internalSub) != 0 ) || ( (j%internalSub) != 0 ) )
						pg().vertex(posi, posj);
				}
			}
			pg().endShape();
			pg().popStyle();
		}	

		@Override
		public void drawZoomWindowHint() {
			float p1x = (float) scene.prosceneMouse.fCorner.getX();
			float p1y = (float) scene.prosceneMouse.fCorner.getY();
			float p2x = (float) scene.prosceneMouse.lCorner.getX();
			float p2y = (float) scene.prosceneMouse.lCorner.getY();
			scene.beginScreenDrawing();
			pg().pushStyle();
			pg().stroke(255, 255, 255);
			pg().strokeWeight(2);
			pg().noFill();
			pg().beginShape();
			pg().vertex(p1x, p1y);
			pg().vertex(p2x, p1y);
			pg().vertex(p2x, p2y);		
			pg().vertex(p1x, p2y);
			pg().endShape(CLOSE);
			pg().popStyle();
			scene.endScreenDrawing();
		}

		@Override
		public void drawScreenRotateLineHint() {
			float p1x = (float) scene.prosceneMouse.lCorner.getX();
			float p1y = (float) scene.prosceneMouse.lCorner.getY();
			Vec p2 = scene.viewport().projectedCoordinatesOf(scene.arcballReferencePoint());
			scene.beginScreenDrawing();
			pg().pushStyle();
			pg().stroke(255, 255, 255);
			pg().strokeWeight(2);
			pg().noFill();
			pg().line(p2.x(), p2.y(), p1x, p1y);
			pg().popStyle();
			scene.endScreenDrawing();
		}

		@Override
		public void drawArcballReferencePointHint() {
			Vec p = scene.viewport().projectedCoordinatesOf(scene.arcballReferencePoint());
			pg().pushStyle();
			pg().stroke(255);
			pg().strokeWeight(3);
			scene.drawCross(p.vec[0], p.vec[1]);
			pg().popStyle();
		}

		@Override
		public void drawCross(float px, float py, float size) {
			scene.beginScreenDrawing();
			pg().pushStyle();		
			//pg().stroke(color);
			//pg().strokeWeight(strokeWeight);
			pg().noFill();
			pg().beginShape(LINES);
			pg().vertex(px - size, py);
			pg().vertex(px + size, py);
			pg().vertex(px, py - size);
			pg().vertex(px, py + size);
			pg().endShape();
			pg().popStyle();
			scene.endScreenDrawing();		
		}

		@Override
		public void drawFilledCircle(int subdivisions, Vec center, float radius) {
			float precision = TWO_PI/subdivisions;
			float x = center.x();
			float y = center.y();
			float angle, x2, y2;
			scene.beginScreenDrawing();
			pg().pushStyle();
			pg().noStroke();
			//pg().fill(color);
			pg().beginShape(TRIANGLE_FAN);		
			pg().vertex(x, y);
			for (angle = 0.0f; angle <= TWO_PI + 1.1*precision; angle += precision) {			
				x2 = x + PApplet.sin(angle) * radius;
				y2 = y + PApplet.cos(angle) * radius;			
				pg().vertex(x2, y2);
			}
			pg().endShape();
			pg().popStyle();
			scene.endScreenDrawing();
		}

		@Override
		public void drawFilledSquare(Vec center, float edge) {
			float x = center.x();
			float y = center.y();
			scene.beginScreenDrawing();		
			pg().pushStyle();
			pg().noStroke();
			//pg().fill(color);
			pg().beginShape(QUADS);
			pg().vertex(x - edge, y + edge);
			pg().vertex(x + edge, y + edge);
			pg().vertex(x + edge, y - edge);
			pg().vertex(x - edge, y - edge);
			pg().endShape();
			pg().popStyle();
			scene.endScreenDrawing();
		}

		@Override
		public void drawShooterTarget(Vec center, float length) {
			float x = center.x();
			float y = center.y();
			scene.beginScreenDrawing();
			
			pg().pushStyle();

			//pg().stroke(color);
			//pg().strokeWeight(strokeWeight);
			pg().noFill();

			pg().beginShape();
			pg().vertex((x - length), (y - length) + (0.6f * length));
			pg().vertex((x - length), (y - length));
			pg().vertex((x - length) + (0.6f * length), (y - length));
			pg().endShape();

			pg().beginShape();
			pg().vertex((x + length) - (0.6f * length), (y - length));
			pg().vertex((x + length), (y - length));
			pg().vertex((x + length), ((y - length) + (0.6f * length)));
			pg().endShape();
			
			pg().beginShape();
			pg().vertex((x + length), ((y + length) - (0.6f * length)));
			pg().vertex((x + length), (y + length));
			pg().vertex(((x + length) - (0.6f * length)), (y + length));
			pg().endShape();

			pg().beginShape();
			pg().vertex((x - length) + (0.6f * length), (y + length));
			pg().vertex((x - length), (y + length));
			pg().vertex((x - length), ((y + length) - (0.6f * length)));
			pg().endShape();

			pg().popStyle();
			scene.endScreenDrawing();

			drawCross(center.x(), center.y(), 0.6f * length);
		}

		@Override
		public void drawWindow(Window camera, float scale) {
			pg().pushMatrix();
			
			/**
			VFrame tmpFrame = new VFrame(scene.is3D());
			tmpFrame.fromMatrix(camera.frame().worldMatrix(), camera.frame().magnitude());		
			scene().applyTransformation(tmpFrame);
			// */
			//Same as above, but easier ;)
		  scene().applyWorldTransformation(camera.frame());

			//upper left coordinates of the near corner
			Vec upperLeft = new Vec();
			
			pg().pushStyle();
			
			/**
			float[] wh = camera.getOrthoWidthHeight();
			upperLeft.x = scale * wh[0];
			upperLeft.y = scale * wh[1];
			*/
			
			upperLeft.x(scale * scene.width() / 2);
			upperLeft.y(scale * scene.height() / 2);
							
			pg().noStroke();		
			pg().beginShape(PApplet.QUADS);				
			pg().vertex(upperLeft.x(), upperLeft.y());
			pg().vertex(-upperLeft.x(), upperLeft.y());
			pg().vertex(-upperLeft.x(), -upperLeft.y());
			pg().vertex(upperLeft.x(), -upperLeft.y());		
			pg().endShape();

			// Up arrow
			float arrowHeight = 1.5f * upperLeft.y();
			float baseHeight = 1.2f * upperLeft.y();
			float arrowHalfWidth = 0.5f * upperLeft.x();
			float baseHalfWidth = 0.3f * upperLeft.x();
			
		  // Base
			pg().beginShape(PApplet.QUADS);		
			if( camera.scene.isLeftHanded() ) {
				pg().vertex(-baseHalfWidth, -upperLeft.y());
				pg().vertex(baseHalfWidth, -upperLeft.y());
				pg().vertex(baseHalfWidth, -baseHeight);
				pg().vertex(-baseHalfWidth, -baseHeight);	
			}
			else {
				pg().vertex(-baseHalfWidth, upperLeft.y());
				pg().vertex(baseHalfWidth, upperLeft.y());
				pg().vertex(baseHalfWidth, baseHeight);
				pg().vertex(-baseHalfWidth, baseHeight);
			}
			pg().endShape();
			
		  // Arrow
			pg().beginShape(PApplet.TRIANGLES);
			if( camera.scene.isLeftHanded() ) {
				pg().vertex(0.0f, -arrowHeight);
				pg().vertex(-arrowHalfWidth, -baseHeight);
				pg().vertex(arrowHalfWidth, -baseHeight);
			}
			else {
				pg().vertex(0.0f, arrowHeight);
				pg().vertex(-arrowHalfWidth, baseHeight);
				pg().vertex(arrowHalfWidth, baseHeight);
			}
			pg().endShape();		
			
			pg().popStyle();
			pg().popMatrix();
		}
		
		 @Override
		  public void cylinder(float w, float h) {
		  	AbstractScene.showDepthWarning("cylinder");
		  }
		  
		  @Override
		 	public void hollowCylinder(int detail, float w, float h, Vec m, Vec n) {
		  	AbstractScene.showDepthWarning("cylinder");
		 	}
		  
		  @Override
		  public void cone(int detail, float x, float y, float r, float h) {
		  	AbstractScene.showDepthWarning("cylinder");
		 	}
		  
		  @Override
		  public void cone(int detail, float x, float y, float r1, float r2, float h) {
		  	AbstractScene.showDepthWarning("cylinder");
		 	}
		  
		  @Override
		  public void drawCamera(Camera camera, boolean drawFarPlane, float scale) {
		  	AbstractScene.showDepthWarning("cylinder");
		 	}

		  @Override
		  public void drawKFIViewport(float scale) {		  	
		  	float halfHeight = scale * 1f;
				float halfWidth = halfHeight * 1.3f;

				float arrowHeight = 1.5f * halfHeight;
				float baseHeight = 1.2f * halfHeight;
				float arrowHalfWidth = 0.5f * halfWidth;
				float baseHalfWidth = 0.3f * halfWidth;

				pg().pushStyle();

				// /**
			  // Frustum outline
				pg().noFill();		
				pg().beginShape();
				pg().vertex(-halfWidth, halfHeight);
				pg().vertex(-halfWidth, -halfHeight);
				pg().vertex(0.0f, 0.0f);
				pg().vertex(halfWidth, -halfHeight);
				pg().vertex(-halfWidth, -halfHeight);
				pg().endShape();
				pg().noFill();
				pg().beginShape();
				pg().vertex(halfWidth, -halfHeight);
				pg().vertex(halfWidth, halfHeight);
				pg().vertex(0.0f, 0.0f);
				pg().vertex(-halfWidth, halfHeight);
				pg().vertex(halfWidth, halfHeight);
				pg().endShape();
				// */

				// Up arrow
				pg().noStroke();
				pg().fill(170);
				// Base
				pg().beginShape(PApplet.QUADS);
				
				if( isLeftHanded() ) {
					pg().vertex(baseHalfWidth, -halfHeight);
					pg().vertex(-baseHalfWidth, -halfHeight);
					pg().vertex(-baseHalfWidth, -baseHeight);
					pg().vertex(baseHalfWidth, -baseHeight);
				}
				else {
					pg().vertex(-baseHalfWidth, halfHeight);
					pg().vertex(baseHalfWidth, halfHeight);
					pg().vertex(baseHalfWidth, baseHeight);
					pg().vertex(-baseHalfWidth, baseHeight);
				}
				
				pg().endShape();
				// Arrow
				pg().beginShape(PApplet.TRIANGLES);
				
				if( isLeftHanded() ) {
					pg().vertex(0.0f, -arrowHeight);
					pg().vertex(arrowHalfWidth, -baseHeight);
					pg().vertex(-arrowHalfWidth, -baseHeight);
				}
				else {
				  pg().vertex(0.0f, arrowHeight);
				  pg().vertex(-arrowHalfWidth, baseHeight);
				  pg().vertex(arrowHalfWidth, baseHeight);
				}
				
				pg().endShape();

				pg().popStyle();
		 	}

		@Override
		public void drawPath(List<RefFrame> path, int mask, int nbFrames,int nbSteps, float scale) {
			if (mask != 0) {
				pg().pushStyle();
				pg().strokeWeight(2);
				pg().noFill();
				pg().stroke(170);
				
				if (((mask & 1) != 0) && path.size() > 1 ) {				
					pg().beginShape();
					for (RefFrame myFr : path)
						pg().vertex(myFr.position().x(), myFr.position().y());
					pg().endShape();
				}
				if ((mask & 6) != 0) {
					int count = 0;
					if (nbFrames > nbSteps)
						nbFrames = nbSteps;
					float goal = 0.0f;

					for (RefFrame myFr : path)
						if ((count++) >= goal) {
							goal += nbSteps / (float) nbFrames;
							pg().pushMatrix();
												  
							scene.applyTransformation(myFr);						

							if ((mask & 2) != 0)
								drawKFIViewport(scale);
							if ((mask & 4) != 0)
								drawAxis(scale / 10.0f);

							pg().popMatrix();
						}
				}
				pg().popStyle();
			}
		}
	}
	
	protected class P5Drawing3D extends P5Drawing2D {
		public P5Drawing3D(Scene scn) {
			super(scn);
		}
		
		public PGraphics3D pg3d() {
		  return (PGraphics3D) pg();	
		}
		
		/**
		 * Overriding of {@link remixlab.remixcam.core.Rendarable#cylinder(float, float)}.
		 * <p>
		 * Code adapted from http://www.processingblogs.org/category/processing-java/ 
		 */
		@Override
		public void cylinder(float w, float h) {
			float px, py;
			
			pg3d().beginShape(PApplet.QUAD_STRIP);
			for (float i = 0; i < 13; i++) {
				px = (float) Math.cos(PApplet.radians(i * 30)) * w;
				py = (float) Math.sin(PApplet.radians(i * 30)) * w;
				pg3d().vertex(px, py, 0);
				pg3d().vertex(px, py, h);
			}
			pg3d().endShape();
			
			pg3d().beginShape(PApplet.TRIANGLE_FAN);
			pg3d().vertex(0, 0, 0);
			for (float i = 12; i > -1; i--) {
				px = (float) Math.cos(PApplet.radians(i * 30)) * w;
				py = (float) Math.sin(PApplet.radians(i * 30)) * w;
				pg3d().vertex(px, py, 0);
			}
			pg3d().endShape();
			
			pg3d().beginShape(PApplet.TRIANGLE_FAN);
			pg3d().vertex(0, 0, h);
			for (float i = 0; i < 13; i++) {
				px = (float) Math.cos(PApplet.radians(i * 30)) * w;
				py = (float) Math.sin(PApplet.radians(i * 30)) * w;
				pg3d().vertex(px, py, h);
			}
			pg3d().endShape();
		}
		
		/**
		 * Convenience function that simply calls
		 * {@code hollowCylinder(20, w, h, new Vector3D(0,0,-1), new Vector3D(0,0,1))}.
		 * 
		 * @see #hollowCylinder(int, float, float, Vec, Vec)
		 * @see #cylinder(float, float)
		 */
		public void hollowCylinder(float w, float h) {
			this.hollowCylinder(20, w, h, new Vec(0,0,-1), new Vec(0,0,1));
		}
		
		/**
		 * Convenience function that simply calls
		 * {@code hollowCylinder(detail, w, h, new Vector3D(0,0,-1), new Vector3D(0,0,1))}.
		 * 
		 * @see #hollowCylinder(int, float, float, Vec, Vec)
		 * @see #cylinder(float, float)
		 */
		public void hollowCylinder(int detail, float w, float h) {
			this.hollowCylinder(detail, w, h, new Vec(0,0,-1), new Vec(0,0,1));
		}
	 
		/**
		 * Draws a cylinder whose bases are formed by two cutting planes ({@code m}
		 * and {@code n}), along the {@link #renderer()} positive {@code z} axis.
		 * 
		 * @param detail
		 * @param w radius of the cylinder and h is its height
		 * @param h height of the cylinder
		 * @param m normal of the plane that intersects the cylinder at z=0
		 * @param n normal of the plane that intersects the cylinder at z=h
		 * 
		 * @see #cylinder(float, float)
		 */
		@Override
		public void hollowCylinder(int detail, float w, float h, Vec m, Vec n) {
			//eqs taken from: http://en.wikipedia.org/wiki/Line-plane_intersection
			Vec pm0 = new Vec(0,0,0);
			Vec pn0 = new Vec(0,0,h);
			Vec l0 = new Vec();		
			Vec l = new Vec(0,0,1);
			Vec p = new Vec();
			float x,y,d;		
			
			pg3d().noStroke();
			pg3d().beginShape(PApplet.QUAD_STRIP);
			
			for (float t = 0; t <= detail; t++) {
				x = w * PApplet.cos(t * TWO_PI/detail);
				y = w * PApplet.sin(t * TWO_PI/detail);
				l0.set(x,y,0);
				
				d = ( m.dot(Vec.sub(pm0, l0)) )/( l.dot(m) );
				p =  Vec.add( Vec.mult(l, d), l0 );
				pg3d().vertex(p.x(), p.y(), p.z());
				
				l0.z(h);
				d = ( n.dot(Vec.sub(pn0, l0)) )/( l.dot(n) );
				p =  Vec.add( Vec.mult(l, d), l0 );
				pg3d().vertex(p.x(), p.y(), p.z());
			}
			pg3d().endShape();
		}

		/**
		 * Overriding of {@link remixlab.dandelion.core.Renderable#cone(int, float, float, float, float)}.
		 * <p>
		 * The code of this function was adapted from
		 * http://processinghacks.com/hacks:cone Thanks to Tom Carden.
		 * 
		 * @see #cone(int, float, float, float, float, float)
		 */
		@Override
		public void cone(int detail, float x, float y, float r, float h) {
			float unitConeX[] = new float[detail + 1];
			float unitConeY[] = new float[detail + 1];

			for (int i = 0; i <= detail; i++) {
				float a1 = PApplet.TWO_PI * i / detail;
				unitConeX[i] = r * (float) Math.cos(a1);
				unitConeY[i] = r * (float) Math.sin(a1);
			}

			pg3d().pushMatrix();
			pg3d().translate(x, y);
			pg3d().beginShape(PApplet.TRIANGLE_FAN);
			pg3d().vertex(0, 0, h);
			for (int i = 0; i <= detail; i++) {
				pg3d().vertex(unitConeX[i], unitConeY[i], 0.0f);
			}
			pg3d().endShape();
			pg3d().popMatrix();
		}

		/**
		 * Overriding of {@link remixlab.dandelion.core.Renderable#cone(int, float, float, float, float, float)}.
		 */
		@Override
		public void cone(int detail, float x, float y, float r1, float r2, float h) {
			float firstCircleX[] = new float[detail + 1];
			float firstCircleY[] = new float[detail + 1];
			float secondCircleX[] = new float[detail + 1];
			float secondCircleY[] = new float[detail + 1];

			for (int i = 0; i <= detail; i++) {
				float a1 = TWO_PI * i / detail;
				firstCircleX[i] = r1 * (float) Math.cos(a1);
				firstCircleY[i] = r1 * (float) Math.sin(a1);
				secondCircleX[i] = r2 * (float) Math.cos(a1);
				secondCircleY[i] = r2 * (float) Math.sin(a1);
			}

			pg3d().pushMatrix();
			pg3d().translate(x, y);
			pg3d().beginShape(PApplet.QUAD_STRIP);
			for (int i = 0; i <= detail; i++) {
				pg3d().vertex(firstCircleX[i], firstCircleY[i], 0);
				pg3d().vertex(secondCircleX[i], secondCircleY[i], h);
			}
			pg3d().endShape();
			pg3d().popMatrix();		
		}

		@Override
		public void drawAxis(float length) {
			final float charWidth = length / 40.0f;
			final float charHeight = length / 30.0f;
			final float charShift = 1.04f * length;

			// pg3d().noLights();

			pg3d().pushStyle();
			
			pg3d().beginShape(PApplet.LINES);		
			pg3d().strokeWeight(2);
			// The X
			pg3d().stroke(200, 0, 0);
			pg3d().vertex(charShift, charWidth, -charHeight);
			pg3d().vertex(charShift, -charWidth, charHeight);
			pg3d().vertex(charShift, -charWidth, -charHeight);
			pg3d().vertex(charShift, charWidth, charHeight);
			// The Y
			pg3d().stroke(0, 200, 0);
			pg3d().vertex(charWidth, charShift, charHeight);
			pg3d().vertex(0.0f, charShift, 0.0f);
			pg3d().vertex(-charWidth, charShift, charHeight);
			pg3d().vertex(0.0f, charShift, 0.0f);
			pg3d().vertex(0.0f, charShift, 0.0f);
			pg3d().vertex(0.0f, charShift, -charHeight);
			// The Z
			pg3d().stroke(0, 100, 200);
			
			//left_handed
			if( isLeftHanded() ) {
				pg3d().vertex(-charWidth, -charHeight, charShift);
				pg3d().vertex(charWidth, -charHeight, charShift);
				pg3d().vertex(charWidth, -charHeight, charShift);
				pg3d().vertex(-charWidth, charHeight, charShift);
				pg3d().vertex(-charWidth, charHeight, charShift);
				pg3d().vertex(charWidth, charHeight, charShift);
			}
			else {
				pg3d().vertex(-charWidth, charHeight, charShift);
				pg3d().vertex(charWidth, charHeight, charShift);
				pg3d().vertex(charWidth, charHeight, charShift);
				pg3d().vertex(-charWidth, -charHeight, charShift);
				pg3d().vertex(-charWidth, -charHeight, charShift);
				pg3d().vertex(charWidth, -charHeight, charShift);
			}
			
			pg3d().endShape();
			
		  /**
			// Z axis
			pg3d().noStroke();
			pg3d().fill(0, 100, 200);
			drawArrow(length, 0.01f * length);

			// X Axis
			pg3d().fill(200, 0, 0);
			pg3d().pushMatrix();
			pg3d().rotateY(HALF_PI);
			drawArrow(length, 0.01f * length);
			pg3d().popMatrix();

			// Y Axis
			pg3d().fill(0, 200, 0);
			pg3d().pushMatrix();
			pg3d().rotateX(-HALF_PI);
			drawArrow(length, 0.01f * length);
			pg3d().popMatrix();
			// */
			
		  // X Axis
			pg3d().stroke(200, 0, 0);
			pg3d().line(0, 0, 0, length, 0, 0);
		  // Y Axis
			pg3d().stroke(0, 200, 0);		
			pg3d().line(0, 0, 0, 0, length, 0);
			// Z Axis
			pg3d().stroke(0, 100, 200);
			pg3d().line(0, 0, 0, 0, 0, length);		

			pg3d().popStyle();
		}
		
		@Override
		public void drawCamera(Camera cam, boolean drawFarPlane, float scale) {
			pg3d().pushMatrix();
			
			//applyMatrix(camera.frame().worldMatrix());
			// same as the previous line, but maybe more efficient
			/**
			VFrame tmpFrame = new VFrame(scene.is3D());
			tmpFrame.fromMatrix(camera.frame().worldMatrix());
			scene().applyTransformation(tmpFrame);
			// */
			//same as above but easier
			
			//fails due to scaling!
			//scene().applyTransformation(camera.frame());
				
			pg3d().translate( cam.frame().translation().vec[0], cam.frame().translation().vec[1], cam.frame().translation().vec[2] );
			pg3d().rotate( cam.frame().rotation().angle(), ((Quat)cam.frame().rotation()).axis().vec[0], ((Quat)cam.frame().rotation()).axis().vec[1], ((Quat)cam.frame().rotation()).axis().vec[2]);

			// 0 is the upper left coordinates of the near corner, 1 for the far one
			Vec[] points = new Vec[2];
			points[0] = new Vec();
			points[1] = new Vec();

			points[0].z(scale * cam.zNear());
			points[1].z(scale * cam.zFar());

			switch (cam.type()) {
			case PERSPECTIVE: {
				points[0].y(points[0].z() * PApplet.tan(cam.fieldOfView() / 2.0f));
				points[0].x(points[0].y() * cam.aspectRatio());
				float ratio = points[1].z() / points[0].z();
				points[1].y(ratio * points[0].y());
				points[1].x(ratio * points[0].x());
				break;
			}
			case ORTHOGRAPHIC: {
				float[] wh = cam.getOrthoWidthHeight();
				//points[0].x = points[1].x = scale * wh[0];
				//points[0].y = points[1].y = scale * wh[1];
				
				points[0].x(scale * wh[0]);
				points[1].x(scale * wh[0]);
				points[0].y(scale * wh[1]); 
				points[1].y(scale * wh[1]);
				break;
			}
			}

			int farIndex = drawFarPlane ? 1 : 0;
			
		  // Frustum lines
			pg3d().pushStyle();		
			pg3d().strokeWeight(2);
			//pg3d().stroke(255,255,0);
			switch (cam.type()) {
				case PERSPECTIVE:
					pg3d().beginShape(PApplet.LINES);
					pg3d().vertex(0.0f, 0.0f, 0.0f);
					pg3d().vertex(points[farIndex].x(), points[farIndex].y(), -points[farIndex].z());
					pg3d().vertex(0.0f, 0.0f, 0.0f);
					pg3d().vertex(-points[farIndex].x(), points[farIndex].y(), -points[farIndex].z());
					pg3d().vertex(0.0f, 0.0f, 0.0f);
					pg3d().vertex(-points[farIndex].x(), -points[farIndex].y(),	-points[farIndex].z());
					pg3d().vertex(0.0f, 0.0f, 0.0f);
					pg3d().vertex(points[farIndex].x(), -points[farIndex].y(), -points[farIndex].z());
					pg3d().endShape();
					break;
				case ORTHOGRAPHIC:
					if (drawFarPlane) {
						pg3d().beginShape(PApplet.LINES);
						pg3d().vertex(points[0].x(), points[0].y(), -points[0].z());
						pg3d().vertex(points[1].x(), points[1].y(), -points[1].z());
						pg3d().vertex(-points[0].x(), points[0].y(), -points[0].z());
						pg3d().vertex(-points[1].x(), points[1].y(), -points[1].z());
						pg3d().vertex(-points[0].x(), -points[0].y(), -points[0].z());
						pg3d().vertex(-points[1].x(), -points[1].y(), -points[1].z());
						pg3d().vertex(points[0].x(), -points[0].y(), -points[0].z());
						pg3d().vertex(points[1].x(), -points[1].y(), -points[1].z());
						pg3d().endShape();
					}
			}
			
			// Near and (optionally) far plane(s)		
			pg3d().noStroke();
			//pg3d().fill(255,255,0,160);
			pg3d().beginShape(PApplet.QUADS);
			for (int i = farIndex; i >= 0; --i) {
				pg3d().normal(0.0f, 0.0f, (i == 0) ? 1.0f : -1.0f);			
				pg3d().vertex(points[i].x(), points[i].y(), -points[i].z());
				pg3d().vertex(-points[i].x(), points[i].y(), -points[i].z());
				pg3d().vertex(-points[i].x(), -points[i].y(), -points[i].z());
				pg3d().vertex(points[i].x(), -points[i].y(), -points[i].z());
			}
			pg3d().endShape();

			// Up arrow
			float arrowHeight = 1.5f * points[0].y();
			float baseHeight = 1.2f * points[0].y();
			float arrowHalfWidth = 0.5f * points[0].x();
			float baseHalfWidth = 0.3f * points[0].x();

			// pg3d().noStroke();
			// Base
			pg3d().beginShape(PApplet.QUADS);		
			if( cam.scene.isLeftHanded() ) {
				pg3d().vertex(-baseHalfWidth, -points[0].y(), -points[0].z());
				pg3d().vertex(baseHalfWidth, -points[0].y(), -points[0].z());
				pg3d().vertex(baseHalfWidth, -baseHeight, -points[0].z());
				pg3d().vertex(-baseHalfWidth, -baseHeight, -points[0].z());
			}
			else {
				pg3d().vertex(-baseHalfWidth, points[0].y(), -points[0].z());
				pg3d().vertex(baseHalfWidth, points[0].y(), -points[0].z());
				pg3d().vertex(baseHalfWidth, baseHeight, -points[0].z());
				pg3d().vertex(-baseHalfWidth, baseHeight, -points[0].z());
			}
			pg3d().endShape();

			// Arrow
			pg3d().beginShape(PApplet.TRIANGLES);
			
			if( cam.scene.isLeftHanded() ) {
				pg3d().vertex(0.0f, -arrowHeight, -points[0].z());
				pg3d().vertex(-arrowHalfWidth, -baseHeight, -points[0].z());
				pg3d().vertex(arrowHalfWidth, -baseHeight, -points[0].z());
			}
			else {
				pg3d().vertex(0.0f, arrowHeight, -points[0].z());
				pg3d().vertex(-arrowHalfWidth, baseHeight, -points[0].z());
				pg3d().vertex(arrowHalfWidth, baseHeight, -points[0].z());
			}
			pg3d().endShape();
			
			pg3d().popStyle();
			pg3d().popMatrix();
		}

		@Override
		public void drawKFIViewport(float scale) {
			float halfHeight = scale * 0.07f;
			float halfWidth = halfHeight * 1.3f;
			float dist = halfHeight / (float) Math.tan(PApplet.PI / 8.0f);

			float arrowHeight = 1.5f * halfHeight;
			float baseHeight = 1.2f * halfHeight;
			float arrowHalfWidth = 0.5f * halfWidth;
			float baseHalfWidth = 0.3f * halfWidth;

			// Frustum outline
			pg3d().pushStyle();

			pg3d().noFill();		
			pg3d().beginShape();
			pg3d().vertex(-halfWidth, halfHeight, -dist);
			pg3d().vertex(-halfWidth, -halfHeight, -dist);
			pg3d().vertex(0.0f, 0.0f, 0.0f);
			pg3d().vertex(halfWidth, -halfHeight, -dist);
			pg3d().vertex(-halfWidth, -halfHeight, -dist);
			pg3d().endShape();
			pg3d().noFill();
			pg3d().beginShape();
			pg3d().vertex(halfWidth, -halfHeight, -dist);
			pg3d().vertex(halfWidth, halfHeight, -dist);
			pg3d().vertex(0.0f, 0.0f, 0.0f);
			pg3d().vertex(-halfWidth, halfHeight, -dist);
			pg3d().vertex(halfWidth, halfHeight, -dist);
			pg3d().endShape();

			// Up arrow
			pg3d().noStroke();
			pg3d().fill(170);
			// Base
			pg3d().beginShape(PApplet.QUADS);
			
			if( isLeftHanded() ) {
				pg3d().vertex(baseHalfWidth, -halfHeight, -dist);
				pg3d().vertex(-baseHalfWidth, -halfHeight, -dist);
				pg3d().vertex(-baseHalfWidth, -baseHeight, -dist);
				pg3d().vertex(baseHalfWidth, -baseHeight, -dist);
			}
			else {
				pg3d().vertex(-baseHalfWidth, halfHeight, -dist);
				pg3d().vertex(baseHalfWidth, halfHeight, -dist);
				pg3d().vertex(baseHalfWidth, baseHeight, -dist);
				pg3d().vertex(-baseHalfWidth, baseHeight, -dist);
			}
			
			pg3d().endShape();
			// Arrow
			pg3d().beginShape(PApplet.TRIANGLES);
			
			if( isLeftHanded() ) {
				pg3d().vertex(0.0f, -arrowHeight, -dist);
				pg3d().vertex(arrowHalfWidth, -baseHeight, -dist);
				pg3d().vertex(-arrowHalfWidth, -baseHeight, -dist);
			}
			else {
			  pg3d().vertex(0.0f, arrowHeight, -dist);
			  pg3d().vertex(-arrowHalfWidth, baseHeight, -dist);
			  pg3d().vertex(arrowHalfWidth, baseHeight, -dist);
			}
			
			pg3d().endShape();

			pg3d().popStyle();
		}
		
		@Override
		public void drawPath(List<RefFrame> path, int mask, int nbFrames, int nbSteps, float scale) {
			if (mask != 0) {
				pg3d().pushStyle();
				pg3d().strokeWeight(2);
				pg3d().noFill();
				pg3d().stroke(170);
				
				if (((mask & 1) != 0) && path.size() > 1 ) {				
					pg3d().beginShape();
					for (RefFrame myFr : path)
						pg3d().vertex(myFr.position().x(), myFr.position().y(), myFr.position().z());
					pg3d().endShape();
				}
				if ((mask & 6) != 0) {
					int count = 0;
					if (nbFrames > nbSteps)
						nbFrames = nbSteps;
					float goal = 0.0f;

					for (RefFrame myFr : path)
						if ((count++) >= goal) {
							goal += nbSteps / (float) nbFrames;
							pg3d().pushMatrix();
												  
							scene.applyTransformation(myFr);						

							if ((mask & 2) != 0)
								drawKFIViewport(scale);
							if ((mask & 4) != 0)
								drawAxis(scale / 10.0f);

							pg3d().popMatrix();
						}
				}
				pg3d().popStyle();
			}
		}
		
		@Override
		public void drawWindow(Window camera, float scale) {
			pg().pushMatrix();
			
			//VFrame tmpFrame = new VFrame(scene.is3D());
			//tmpFrame.fromMatrix(camera.frame().worldMatrix(), camera.frame().magnitude());		
			//scene().applyTransformation(tmpFrame);
			
			//Same as above, but easier ;)
		  scene().applyWorldTransformation(camera.frame());

			//upper left coordinates of the near corner
			Vec upperLeft = new Vec();
			
			pg().pushStyle();
			
			//float[] wh = camera.getOrthoWidthHeight();
			//upperLeft.x = scale * wh[0];
			//upperLeft.y = scale * wh[1];
			
			upperLeft.x(scale * scene.width() / 2);
			upperLeft.y(scale * scene.height() / 2);
							
			pg().noStroke();		
			pg().beginShape(PApplet.QUADS);				
			pg().vertex(upperLeft.x(), upperLeft.y());
			pg().vertex(-upperLeft.x(), upperLeft.y());
			pg().vertex(-upperLeft.x(), -upperLeft.y());
			pg().vertex(upperLeft.x(), -upperLeft.y());		
			pg().endShape();

			// Up arrow
			float arrowHeight = 1.5f * upperLeft.y();
			float baseHeight = 1.2f * upperLeft.y();
			float arrowHalfWidth = 0.5f * upperLeft.x();
			float baseHalfWidth = 0.3f * upperLeft.x();
			
		  // Base
			pg().beginShape(PApplet.QUADS);		
			if( camera.scene.isLeftHanded() ) {
				pg().vertex(-baseHalfWidth, -upperLeft.y());
				pg().vertex(baseHalfWidth, -upperLeft.y());
				pg().vertex(baseHalfWidth, -baseHeight);
				pg().vertex(-baseHalfWidth, -baseHeight);	
			}
			else {
				pg().vertex(-baseHalfWidth, upperLeft.y());
				pg().vertex(baseHalfWidth, upperLeft.y());
				pg().vertex(baseHalfWidth, baseHeight);
				pg().vertex(-baseHalfWidth, baseHeight);
			}
			pg().endShape();
			
		  // Arrow
			pg().beginShape(PApplet.TRIANGLES);
			if( camera.scene.isLeftHanded() ) {
				pg().vertex(0.0f, -arrowHeight);
				pg().vertex(-arrowHalfWidth, -baseHeight);
				pg().vertex(arrowHalfWidth, -baseHeight);
			}
			else {
				pg().vertex(0.0f, arrowHeight);
				pg().vertex(-arrowHalfWidth, baseHeight);
				pg().vertex(arrowHalfWidth, baseHeight);
			}
			pg().endShape();		
			
			pg().popStyle();
			pg().popMatrix();
		}
		
		/**
		@Override
		public void drawGrid(float size, int nbSubdivisions) {
			pg().pushStyle();
			pg().stroke(170, 170, 170);
			pg().strokeWeight(1);
			pg().beginShape(PApplet.LINES);
			for (int i = 0; i <= nbSubdivisions; ++i) {
				final float pos = size * (2.0f * i / nbSubdivisions - 1.0f);
				pg().vertex(pos, -size);
				pg().vertex(pos, +size);
				pg().vertex(-size, pos);
				pg().vertex(size, pos);
			}
			pg().endShape();
			pg().popStyle();
		}
		*/
	}
	
	protected class P5RendererJava2D extends Renderer {
		PGraphics pg;
		Mat proj;
		
		public P5RendererJava2D(Scene scn, PGraphics renderer, Depictable d) {
			super(scn, d);
			pg = renderer;
		}
		
		public P5RendererJava2D(Scene scn, PGraphics renderer) {
			super(scn, new P5Drawing2D(scn));
			pg = renderer;
		}		
		
		public PGraphics pg() {
			return pg;
		}
		
		public PGraphicsJava2D pgj2d() {
		  return (PGraphicsJava2D) pg();	
		}

		@Override
		public boolean is3D() {
			return false;
		}
	}
	
	protected abstract class P5Renderer extends ProjectionRenderer {
		PGraphicsOpenGL pg;
		Mat proj;
		
		public P5Renderer(Scene scn, PGraphicsOpenGL renderer, Depictable d) {
			super(scn, d);
			pg = renderer;
			proj = new Mat();
		}
		
		public PGraphics pg() {
			return pg;
		}
		
		public PGraphicsOpenGL pggl() {
		  return (PGraphicsOpenGL) pg();	
		}	
		
		@Override
		public void pushProjection() {
			pggl().pushProjection();		
		}

		@Override
		public void popProjection() {
			pggl().popProjection();
		}

		@Override
		public void resetProjection() {
			pggl().resetProjection();
		}
		
		@Override
		public Mat getProjection() {
			PMatrix3D pM = pggl().projection.get();
	    return new Mat(pM.get(new float[16]), true);// set it transposed
		}

		@Override
		public Mat getProjection(Mat target) {
			PMatrix3D pM = pggl().projection.get();
	    target.setTransposed(pM.get(new float[16]));
	    return target;
		}

		@Override
		public void applyProjection(Mat source) {
			PMatrix3D pM = new PMatrix3D();
	    pM.set(source.getTransposed(new float[16]));
	    pggl().applyProjection(pM);		
		}

		@Override
		public void applyProjectionRowMajorOrder(float n00, float n01, float n02,
				float n03, float n10, float n11, float n12, float n13, float n20,
				float n21, float n22, float n23, float n30, float n31, float n32,
				float n33) {
			pggl().applyProjection(new PMatrix3D(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23, n30, n31, n32, n33));
		}
	}
	
	protected class P5Renderer2D extends P5Renderer {	
		public P5Renderer2D(Scene scn, PGraphicsOpenGL renderer) {
			super(scn, renderer, new P5Drawing2D(scn));
		}
		
		@Override
		public boolean is3D() {
			return false;
		}
		
		public PGraphics2D pg2d() {
		  return (PGraphics2D) pg();	
		}	

		@Override
		public void setProjection(Mat source) {
			PMatrix3D pM = new PMatrix3D();
			pM.set(source.getTransposed(new float[16]));		
			pg2d().projection.set(pM);		
		}

		@Override
		public void setMatrix(Mat source) {
			PMatrix3D pM = new PMatrix3D();
			pM.set(source.getTransposed(new float[16]));
			//pg2d().setMatrix(pM);
	    pg2d().modelview.set(pM);
		}	

		/**
		 * Sets the processing camera projection matrix from {@link #camera()}. Calls
		 * {@code PApplet.perspective()} or {@code PApplet.orhto()} depending on the
		 * {@link remixlab.dandelion.core.Camera#type()}.
		 */
		@Override
		protected void setProjectionMatrix() {
		  // All options work seemlessly
			/**		
			// Option 1
			Matrix3D mat = new Matrix3D();		
			scene.viewWindow().getProjectionMatrix(mat, true);
			mat.transpose();		
			float[] target = new float[16];
			pg2d().projection.set(mat.get(target));		
			// */	  

			/**		
			// Option 2		
			pg2d().projection.set(scene.viewWindow().getProjectionMatrix(true).getTransposed(new float[16]));
			// */

			// /**
		  // option 3 (new, Andres suggestion)
			// /**		
			proj = scene.window().getProjectionMatrix(true);
			pg2d().setProjection(new PMatrix3D( proj.mat[0],  proj.mat[4], proj.mat[8],  proj.mat[12],
		                                      proj.mat[1],  proj.mat[5], proj.mat[9],  proj.mat[13],
		                                      proj.mat[2],  proj.mat[6], proj.mat[10], proj.mat[14],
		                                      proj.mat[3],  proj.mat[7], proj.mat[11], proj.mat[15] ));
			// */

			/**
			proj = scene.viewWindow().getProjectionMatrix(true);
			pg2d().flush();
		  pg2d().projection.set( proj.mat[0], proj.mat[4],                                  proj.mat[8],  proj.mat[12],
			                       proj.mat[1], isLeftHanded() ? proj.mat[5] : -proj.mat[5], proj.mat[9],  proj.mat[13],
			                       proj.mat[2], proj.mat[6],                                  proj.mat[10], proj.mat[14],
			                       proj.mat[3], proj.mat[7],                                  proj.mat[11], proj.mat[15] );
			pg2d().updateProjmodelview();
			// */
		}

		/**
		 * Sets the processing camera matrix from {@link #camera()}. Simply calls
		 * {@code PApplet.camera()}.
		 */
		@Override
		protected void setModelViewMatrix() {
		  // The two options work seamlessly
			/**		
			// Option 1
			Matrix3D mat = new Matrix3D();		
			scene.viewWindow().getViewMatrix(mat, true);
			mat.transpose();// experimental
			float[] target = new float[16];
			pg2d().modelview.set(mat.get(target));
			// */

			// /**		
			// Option 2
			pg2d().modelview.set(scene.window().getViewMatrix(true).getTransposed(new float[16]));						
			// Finally, caches projmodelview
			//pg2d().projmodelview.set(scene.viewWindow().getProjectionViewMatrix(true).getTransposed(new float[16]));		
			Mat.mult(proj, scene.window().view(), scene.window().projectionView());
			pg2d().projmodelview.set(scene.window().getProjectionViewMatrix(false).getTransposed(new float[16]));
		  // */
		}
	}
	
	protected class P5Renderer3D extends P5Renderer {
		Vec at;	
		
		public P5Renderer3D(Scene scn, PGraphicsOpenGL renderer) {
			super(scn, renderer, new P5Drawing3D(scn));		
			at = new Vec();		
		}
		
		public PGraphics3D pg3d() {
		  return (PGraphics3D) pg();	
		}
		
		@Override
		public boolean is3D() {
			return true;
		}	

		@Override
		public void setProjection(Mat source) {
			PMatrix3D pM = new PMatrix3D();
	    pM.set(source.getTransposed(new float[16]));
	    pg3d().setProjection(pM);
		}
		
		@Override
		public void setMatrix(Mat source) {
			PMatrix3D pM = new PMatrix3D();
			pM.set(source.getTransposed(new float[16]));
			pg3d().setMatrix(pM);//needs testing in screen drawing
		}	
		
		//---

		/**
	   * Sets the processing camera projection matrix from {@link #camera()}. Calls
	   * {@code PApplet.perspective()} or {@code PApplet.orhto()} depending on the
	   * {@link remixlab.dandelion.core.Camera#type()}.
	   */
		@Override
		protected void setProjectionMatrix() {
		  // All options work seemlessly
		  /**
		  // Option 1
		  Matrix3D mat = new Matrix3D();
		  scene.camera().getProjectionMatrix(mat, true);
		  mat.transpose();
		  float[] target = new float[16];
		  pg3d().projection.set(mat.get(target));
		  // */	

		  /**
		  // Option 2
		  pg3d().projection.set(scene.camera().getProjectionMatrix(true).getTransposed(new float[16]));
		  // */

		  // /**
		  // option 3 (new, Andres suggestion)
		  //proj.set((scene.camera().getProjectionMatrix(true).getTransposed(new float[16])));
		  proj = scene.camera().getProjectionMatrix(true);
		  pg3d().setProjection(new PMatrix3D( proj.mat[0], proj.mat[4], proj.mat[8], proj.mat[12],
		  																		proj.mat[1], proj.mat[5], proj.mat[9], proj.mat[13],
		  																		proj.mat[2], proj.mat[6], proj.mat[10], proj.mat[14],
		  																		proj.mat[3], proj.mat[7], proj.mat[11], proj.mat[15] ));
		  // */

		  /**
		  proj = scene.camera().getProjectionMatrix(true);
		  pg3d().flush();
		  pg3d().projection.set( proj.mat[0], proj.mat[4], proj.mat[8], proj.mat[12],
		  proj.mat[1], isLeftHanded() ? proj.mat[5] : -proj.mat[5], proj.mat[9], proj.mat[13],
		  proj.mat[2], proj.mat[6], proj.mat[10], proj.mat[14],
		  proj.mat[3], proj.mat[7], proj.mat[11], proj.mat[15] );
		  pg3d().updateProjmodelview();//only in P5-head
		  // */	

		  /**
		  // Option 4
		  // compute the processing camera projection matrix from our camera() parameters
		  switch (scene.camera().type()) {
		  case PERSPECTIVE:
		  pg3d().perspective(scene.camera().fieldOfView(), scene.camera().aspectRatio(), scene.camera().zNear(), scene.camera().zFar());
		  break;
		  case ORTHOGRAPHIC:
		  float[] wh = scene.camera().getOrthoWidthHeight();
		  pg3d().ortho(-wh[0], wh[0], -wh[1], wh[1], scene.camera().zNear(), scene.camera().zFar());
		  break;
		  }
		  // hack:
		  //if(this.isRightHanded())
		  //pg3d().projection.m11 = -pg3d().projection.m11;
		  // We cache the processing camera projection matrix into our camera()
		  scene.camera().setProjectionMatrix( pg3d().projection.get(new float[16]), true ); // set it transposed
		  // */
		}

		/**
		* Sets the processing camera matrix from {@link #camera()}. Simply calls
		* {@code PApplet.camera()}.
		*/	
		@Override
		protected void setModelViewMatrix() {
		  // All three options work seamlessly
		  /**
		  // Option 1
		  Matrix3D mat = new Matrix3D();
		  scene.camera().getViewMatrix(mat, true);
		  mat.transpose();// experimental
		  float[] target = new float[16];
		  pg3d().modelview.set(mat.get(target));
		  //caches projmodelview
		  pg3d().projmodelview.set(scene.camera().getProjectionViewMatrix(true).getTransposed(new float[16]));
		  // */

		  /**
		  // Option 2
		  pg3d().modelview.set(scene.camera().getViewMatrix(true).getTransposed(new float[16]));
		  // Finally, caches projmodelview
		  //pg3d().projmodelview.set(scene.camera().getProjectionViewMatrix(true).getTransposed(new float[16]));
		  Matrix3D.mult(proj, scene.camera().view(), scene.camera().projectionView());
		  pg3d().projmodelview.set(scene.camera().getProjectionViewMatrix(false).getTransposed(new float[16]));
		  // */	

		  // /**
		  // Option 3
		  // compute the processing camera modelview matrix from our camera() parameters
		  at = scene.camera().at();
		  pg3d().camera(scene.camera().position().x(), scene.camera().position().y(), scene.camera().position().z(),
		  //scene.camera().at().x(), scene.camera().at().y(), scene.camera().at().z(),
		  at.x(), at.y(), at.z(),
		  scene.camera().upVector().x(), scene.camera().upVector().y(), scene.camera().upVector().z());
		  // We cache the processing camera modelview matrix into our camera()
		  scene.camera().setViewMatrix( pg3d().modelview.get(new float[16]), true );// set it transposed
		  // We cache the processing camera projmodelview matrix into our camera()
		  scene.camera().setProjectionViewMatrix( pg3d().projmodelview.get(new float[16]), true );// set it transposed
		  // */
		}	
	}
	
	// ---- //
	
	// proscene version
  public static final String prettyVersion = "2.0.0-alpha.1";
		
	public static final String version = "16";
	
	// P R O C E S S I N G   A P P L E T   A N D   O B J E C T S
	public PApplet parent;
	
	// H A R D W A R E
  protected ProsceneMouse prosceneMouse;
  protected ProsceneKeyboard prosceneKeyboard;
	
	// E X C E P T I O N H A N D L I N G	
  protected int beginOffScreenDrawingCalls;  
  	
	/**
	// M O U S E   G R A B B E R   H I N T   C O L O R S
	private int onSelectionHintColor;
	private int offSelectionHintColor;
	private int cameraPathOnSelectionHintColor;
	private int cameraPathOffSelectionHintColor;
	*/

	// R E G I S T E R   D R A W   A N D   A N I M A T I O N   M E T H O D S
	// Draw
	/** The object to handle the draw event */
	protected Object drawHandlerObject;
	/** The method in drawHandlerObject to execute */
	protected Method drawHandlerMethod;
	/** the name of the method to handle the event */
	protected String drawHandlerMethodName;
	// Animation
	/** The object to handle the animation */
	protected Object animateHandlerObject;
	/** The method in animateHandlerObject to execute */
	protected Method animateHandlerMethod;
	/** the name of the method to handle the animation */
	protected String animateHandlerMethodName;	
	
	protected boolean javaTiming;

	/**
	 * Constructor that defines an on-screen Scene (the one that most likely
	 * would just fulfill all of your needs). All viewer parameters (display flags,
	 * scene parameters, associated objects...) are set to their default values.
	 * See the associated documentation. This is actually just a convenience
	 * function that simply calls {@code this(p, (PGraphicsOpenGL) p.g)}. Call any
	 * other constructor by yourself to possibly define an off-screen Scene.
	 * 
	 * @see #Scene(PApplet, PGraphics)
	 * @see #Scene(PApplet, PGraphics, int, int)
	 */	
	public Scene(PApplet p) {
		this(p, p.g);		
	}
	
	/**
	 * This constructor is typically used to define an off-screen Scene. This is
	 * accomplished simply by specifying a custom {@code renderer}, different
	 * from the PApplet's renderer. All viewer parameters (display flags, scene
	 * parameters, associated objects...) are set to their default values. This
	 * is actually just a convenience function that simply calls
	 * {@code this(p, renderer, 0, 0)}. If you plan to define an on-screen Scene,
	 * call {@link #Scene(PApplet)} instead.
	 * 
	 * @see #Scene(PApplet)
	 * @see #Scene(PApplet, PGraphics, int, int)
	 */
	public Scene(PApplet p, PGraphics renderer) {	
		this(p, renderer, 0, 0);
	}

	/**
	 * This constructor is typically used to define an off-screen Scene. This is
	 * accomplished simply by specifying a custom {@code renderer}, different
	 * from the PApplet's renderer. All viewer parameters (display flags, scene
	 * parameters, associated objects...) are set to their default values. The
	 * {@code x} and {@code y} parameters define the position of the upper-left
	 * corner where the off-screen Scene is expected to be displayed, e.g., for
	 * instance with a call to the Processing built-in {@code image(img, x, y)}
	 * function. If {@link #isOffscreen()} returns {@code false} (i.e.,
	 * {@link #renderer()} equals the PApplet's renderer), the values of x and y
	 * are meaningless (both are set to 0 to be taken as dummy values). If you
	 * plan to define an on-screen Scene, call {@link #Scene(PApplet)} instead. 
	 * 
	 * @see #Scene(PApplet)
	 * @see #Scene(PApplet, PGraphicsOpenGL)
	 */
	public Scene(PApplet p, PGraphics pg, int x, int y) {
		parent = p;
		
		if( pg instanceof PGraphicsJava2D )
			setRenderer( new P5RendererJava2D(this, (PGraphicsJava2D)pg) );	
		else
			if( pg instanceof PGraphics2D )
				setRenderer( new P5Renderer2D(this, (PGraphics2D)pg) );
			else
				if( pg instanceof PGraphics3D )
					setRenderer( new P5Renderer3D(this, (PGraphics3D)pg) );
		
		width = pg.width;
		height = pg.height;
		
		if(is2D())
			this.setDottedGrid(false);
		
		//setJavaTimers();
		this.parent.frameRate(100);
		setLeftHanded();
		
		/**
		// TODO decide if this should go
		//mouse grabber selection hint colors		
		setMouseGrabberOnSelectionHintColor(pg3d.color(0, 0, 255));
		setMouseGrabberOffSelectionHintColor(pg3d.color(255, 0, 0));
		setMouseGrabberCameraPathOnSelectionHintColor(pg3d.color(255, 255, 0));
		setMouseGrabberCameraPathOffSelectionHintColor(pg3d.color(0, 255, 255));
		*/		
		
		// 1 ->
		avatarIsInteractiveFrame = false;// also init in setAvatar, but we
		// need it here to properly init the camera
		avatarIsInteractiveAvatarFrame = false;// also init in setAvatar, but we
		// need it here to properly init the camera
		
		if( is3D() )
			vport = new Camera(this);
		else
			vport = new Window(this);
		setViewPort(viewport());//calls showAll();
		
		setAvatar(null);
		
  	// This scene is offscreen if the provided renderer is
		// different from the main PApplet renderer.
		offscreen = pg != p.g;
		if(offscreen)
			upperLeftCorner = new Point(x, y);
		else
			upperLeftCorner = new Point(0, 0);
		beginOffScreenDrawingCalls = 0;		
		//setDeviceTracking(true);
		//setDeviceGrabber(null);
		
		//deviceGrabberIsAnIFrame = false;

		//withConstraint = true;

		setAxisIsDrawn(true);
		setGridIsDrawn(true);
		setFrameSelectionHintIsDrawn(false);
		setViewportPathsAreDrawn(false);
		
		disableFrustumEquationsUpdate();
		
		enableDefaultKeyboardAgent();
		enableDefaultMouseAgent();

		parent.registerMethod("pre", this);
		parent.registerMethod("draw", this);
		// parent.registerPost(this);
		//parseKeyXxxxMethods();
		//parseMouseXxxxMethods();

		// register draw method
		removeDrawHandler();
	  // register animation method
		removeAnimationHandler();

		// called only once
		init();
	}
	
	public ProsceneMouse defaultMouseAgent() {
		return prosceneMouse;
	}
	
	public ProsceneKeyboard defaultKeyboardAgent() {
		return prosceneKeyboard;
	}
	
	public boolean isDefaultMouseAgentInUse() {
		if(prosceneMouse == null)
			return false;
	  return terseHandler().isAgentRegistered(prosceneMouse);
	}
	
	public boolean isDefaultKeyboardAgentInUse() {
		if(prosceneKeyboard == null)
			return false;
	  return terseHandler().isAgentRegistered(prosceneKeyboard);
	}
	
	/**
	 * Enables Proscene keyboard handling.
	 * 
	 * @see #isDefaultKeyboardAgentInUse()
	 * @see #enableDefaultMouseAgent()
	 * @see #disableDefaultKeyboardAgent()
	 */
	public void enableDefaultKeyboardAgent() {
		if( !isDefaultKeyboardAgentInUse() ) {
			if(prosceneKeyboard == null)
				prosceneKeyboard = new ProsceneKeyboard(this, "proscene_keyboard");
			else
				terseHandler().registerAgent(prosceneKeyboard);			
			parent.registerMethod("keyEvent", prosceneKeyboard);
		}
	}

	/**
	 * Disables Proscene keyboard handling.
	 * 
	 * @see #isDefaultKeyboardAgentInUse()
	 */
	public void disableDefaultKeyboardAgent() {
		if( isDefaultKeyboardAgentInUse() ) {
			terseHandler().unregisterAgent(prosceneKeyboard);
			parent.unregisterMethod("keyEvent", prosceneKeyboard);
		}
	}

	/**
	 * Enables Proscene mouse handling.
	 * 
	 * @see #isDefaultMouseAgentInUse()
	 * @see #disableDefaultMouseAgent()
	 * @see #enableDefaultKeyboardAgent()
	 */
	public void enableDefaultMouseAgent() {
		if( !isDefaultMouseAgentInUse() ) {
			if( prosceneMouse == null )
				prosceneMouse = new ProsceneMouse(this, "proscene_mouse");
			else
				terseHandler().registerAgent(prosceneMouse);
			parent.registerMethod("mouseEvent", prosceneMouse);
		}
	}
	
	/**
	 * Disables Proscene mouse handling.
	 * 
	 * @see #isDefaultMouseAgentInUse()
	 */
	public void disableDefaultMouseAgent() {
		if( isDefaultMouseAgentInUse() ) {
			terseHandler().unregisterAgent(prosceneMouse);
			parent.unregisterMethod("mouseEvent", prosceneMouse);
		}
	}
	
	// matrix stuff
	
	@Override
	public void pushMatrix() {
		pg().pushMatrix();
	}
	
	@Override
	public void popMatrix() {
		pg().popMatrix();
	}
	
	@Override
	public void resetMatrix() {
		pg().resetMatrix();
	}
	
	@Override
	public Mat getMatrix() {
		PMatrix3D pM = (PMatrix3D) pg().getMatrix();
		return new Mat(pM.get(new float[16]), true);// set it transposed
	}
	
	@Override
	public Mat getMatrix(Mat target) {
		PMatrix3D pM = (PMatrix3D) pg().getMatrix();
		target.setTransposed(pM.get(new float[16]));
		return target;
	}
	
	@Override
	public void setMatrix(Mat source) {
		resetMatrix();
		applyMatrix(source);
	}
	
	@Override
	public void printMatrix() {
		pg().printMatrix();
	}
	
	@Override
	public void applyMatrix(Mat source) {
		PMatrix3D pM = new PMatrix3D();
		pM.set(source.getTransposed(new float[16]));
		pg().applyMatrix(pM);
	}
	
	@Override
	public void applyMatrixRowMajorOrder(float n00, float n01, float n02, float n03,
			                                 float n10, float n11, float n12, float n13,
			                                 float n20, float n21, float n22, float n23,
			                                 float n30, float n31, float n32, float n33) {
		pg().applyMatrix(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22,	n23, n30, n31, n32, n33);
	}	
	
	//
	
	@Override
	public void translate(float tx, float ty) {
		pg().translate(tx, ty);		
	}

	@Override
	public void translate(float tx, float ty, float tz) {
		pg().translate(tx, ty, tz);	
	}
	
	@Override
	public void rotate(float angle) {
		pg().rotate(angle);		
	}

	@Override
	public void rotateX(float angle) {
		pg().rotateX(angle);		
	}

	@Override
	public void rotateY(float angle) {
		pg().rotateY(angle);
	}

	@Override
	public void rotateZ(float angle) {
		pg().rotateZ(angle);
	}
	
	@Override
	public void rotate(float angle, float vx, float vy, float vz) {
		pg().rotate(angle, vx, vy, vz);
	}
	
	@Override
	public void scale(float s) {
		pg().scale(s);	
	}

	@Override
	public void scale(float sx, float sy) {
		pg().scale(sx, sy);	
	}

	@Override
	public void scale(float x, float y, float z) {
		pg().scale(x, y, z);
	}

	// 2. Associated objects	
	
	@Override
	public void registerJob(AbstractTimerJob job) {
		if (timingIsSingleThreaded())
			timerHandler().registerJob(job);
		else
			timerHandler().registerJob(job, new TimerWrap(this, job));
	}
	
	public void setJavaTimers() {
		if( !timingIsSingleThreaded() )
			return;
		
		boolean isActive;
		
		for ( AbstractTimerJob job : timerHandler().timerPool() ) {
			long period = 0;
			boolean rOnce = false;
			isActive = job.isActive();
			if(isActive) {
				period = job.period();
				rOnce = job.timer().isSingleShot();
			}
			job.stop();
			job.setTimer(new TimerWrap(this, job));			
			if(isActive) {
				if(rOnce)
					job.runOnce(period);
				else
					job.run(period);
			}
		}	
		
		javaTiming = true;
		PApplet.println("awt timers set");
	}
	
	public boolean timingIsSingleThreaded() {
		return !javaTiming;
	}
	
	public void switchTimers() {
		if( timingIsSingleThreaded() )
			setJavaTimers();
		else
			setSingleThreadedTimers();
	}
	
	public void setSingleThreadedTimers() {
		javaTiming = false;
		timerHandler().restoreTimers();
	}
	
	// 5. Drawing methods

	/**
	 * Internal use. Display various on-screen visual hints to be called from {@link #pre()}
	 * or {@link #draw()}.
	 */
	@Override
	protected void displayVisualHints() {		
		if (frameSelectionHintIsDrawn())
			drawSelectionHints();
		if (viewportPathsAreDrawn()) {
			viewport().drawAllPaths();
			drawViewportPathSelectionHints();
		} else {
			viewport().hideAllPaths();
		}
		if (prosceneMouse.zoomOnRegion)
			drawZoomWindowHint();
		if (prosceneMouse.screenRotate)
			drawScreenRotateLineHint();
		if (viewport().frame().arpFlag) 
			drawArcballReferencePointHint();
		if (viewport().frame().pupFlag) {
			Vec v = viewport().projectedCoordinatesOf(viewport().frame().pupVec);
			pg().pushStyle();		
			pg().stroke(255);
			pg().strokeWeight(3);
			drawCross(v.vec[0], v.vec[1]);
			pg().popStyle();
		}
	}	

	/**
	 * Paint method which is called just before your {@code PApplet.draw()}
	 * method. This method is registered at the PApplet and hence you don't need
	 * to call it.
	 * <p>
	 * Sets the processing camera parameters from {@link #viewport()} and updates
	 * the frustum planes equations if {@link #enableFrustumEquationsUpdate(boolean)}
	 * has been set to {@code true}.
	 */
	public void pre() {
		if (isOffscreen()) return;		
		
		if ((width != pg().width) || (height != pg().height)) {
			width = pg().width;
			height = pg().height;				
			viewport().setScreenWidthAndHeight(width, height);				
		}
		preDraw();
	}

	/**
	 * Paint method which is called just after your {@code PApplet.draw()} method.
	 * This method is registered at the PApplet and hence you don't need to call
	 * it. Calls {@link #drawCommon()}.
	 * 
	 * @see #drawCommon()
	 */
	public void draw() {
		if (isOffscreen()) return;
		postDraw();
	}	
	
	@Override
	protected void invokeRegisteredMethod() {
     	// 3. Draw external registered method
			if (drawHandlerObject != null) {
				try {
					drawHandlerMethod.invoke(drawHandlerObject, new Object[] { this });
				} catch (Exception e) {
					PApplet.println("Something went wrong when invoking your "	+ drawHandlerMethodName + " method");
					e.printStackTrace();
				}
			}	
	}	

	/**
	 * This method should be called when using offscreen rendering 
	 * right after renderer.beginDraw().
   */	
	public void beginDraw() {
		if (isOffscreen()) {
			if (beginOffScreenDrawingCalls != 0)
				throw new RuntimeException(
						"There should be exactly one beginDraw() call followed by a "
								+ "endDraw() and they cannot be nested. Check your implementation!");			
			beginOffScreenDrawingCalls++;			
			preDraw();	
		}
	}

	/**
	 * This method should be called when using offscreen rendering 
	 * right before renderer.endDraw(). Calls {@link #drawCommon()}.
	 * 
	 * @see #drawCommon() 
   */		
	public void endDraw() {
		beginOffScreenDrawingCalls--;
		
		if (beginOffScreenDrawingCalls != 0)
			throw new RuntimeException(
					"There should be exactly one beginDraw() call followed by a "
							+ "endDraw() and they cannot be nested. Check your implementation!");
		
		postDraw();
	}
	
	@Override
	protected void updateCursor() {
		pcursorX = cursorX;
		pcursorY = cursorY;
		cursorX = parent.mouseX;
		cursorY = parent.mouseY;
	}
	
  // 4. Scene dimensions
	
	/**
	@Override
	public float frameRate() {
		return parent.frameRate;
	}
	*/

	/**
	@Override
	public long frameCount() {
		return parent.frameCount;
	}
	*/

	// 6. Display of visual hints and Display methods		
	
	// 2. CAMERA	
	
	// 3. KEYFRAMEINTERPOLATOR CAMERA
	
	/**
	 * Sets the mouse grabber on selection hint {@code color}
	 * (drawn as a shooter target).
	 * 
	 * @see #drawSelectionHints()
	 */
  //public void setMouseGrabberOnSelectionHintColor(int color) { 	onSelectionHintColor = color; }
	
  /**
	 * Sets the mouse grabber off selection hint {@code color}
	 * (drawn as a shooter target).
	 * 
	 * @see #drawSelectionHints()
	 */  
	//public void setMouseGrabberOffSelectionHintColor(int color) { offSelectionHintColor = color;	}
	
	/**
	 * Returns the mouse grabber on selection hint {@code color}.
	 * 
	 * @see #drawSelectionHints()
	 */
	//public int mouseGrabberOnSelectionHintColor() {	return onSelectionHintColor;}
	
	/**
	 * Returns the mouse grabber off selection hint {@code color}.
	 * 
	 * @see #drawSelectionHints()
	 */
  //public int mouseGrabberOffSelectionHintColor() {return offSelectionHintColor;}
  
  /**
	 * Sets the mouse grabber on selection hint {@code color} for camera paths
	 * (drawn as a shooter target).
	 * 
	 * @see #drawCameraPathSelectionHints()
	 */
  // public void setMouseGrabberCameraPathOnSelectionHintColor(int color) {	cameraPathOnSelectionHintColor = color; }
	
  /**
	 * Sets the mouse grabber off selection hint {@code color} for camera paths
	 * (drawn as a shooter target).
	 * 
	 * @see #drawCameraPathSelectionHints()
	 */
	//public void setMouseGrabberCameraPathOffSelectionHintColor(int color) {	cameraPathOffSelectionHintColor = color;	}
	
	/**
	 * Returns the mouse grabber on selection hint {@code color} for camera paths.
	 * 
	 * @see #drawCameraPathSelectionHints()
	 */
	//public int mouseGrabberCameraPathOnSelectionHintColor() {	return cameraPathOnSelectionHintColor;	}
	
	/**
	 * Returns the mouse grabber off selection hint {@code color} for camera paths.
	 * 
	 * @see #drawViewportPathSelectionHints()
	 */
  //public int mouseGrabberCameraPathOffSelectionHintColor() {	return cameraPathOffSelectionHintColor;	}
	
	public PGraphics pg() {
		/**
		if( renderer() instanceof P5Renderer )
			return ((P5Renderer)renderer()).pg();
		*/
		if( renderer() instanceof P5Renderer2D )
			return ((P5Renderer2D)renderer()).pg();
		if( renderer() instanceof P5Renderer3D )
			return ((P5Renderer3D)renderer()).pg();
		//if( renderer() instanceof P5RendererJava2D )
		return ((P5RendererJava2D)renderer()).pg();
	}
	
	public PGraphicsJava2D pgj2d() {
		if (pg() instanceof PGraphicsJava2D)
			return (PGraphicsJava2D) pg();
		else 
			throw new RuntimeException("pGraphics is not instance of PGraphicsJava2D");		
	}
	
	public PGraphicsOpenGL pggl() {
		if (pg() instanceof PGraphicsOpenGL)
			return (PGraphicsOpenGL) pg();
		else 
			throw new RuntimeException("pGraphics is not instance of PGraphicsOpenGL");		
	}
	
	public PGraphics2D pg2d() {
		if (pg() instanceof PGraphics2D)
			return ((P5Renderer2D) renderer()).pg2d();
		else 
			throw new RuntimeException("pGraphics is not instance of PGraphics2D");		
	}
	
	public PGraphics3D pg3d() {
		if (pg() instanceof PGraphics3D)
			return ((P5Renderer3D) renderer()).pg3d();
		else 
			throw new RuntimeException("pGraphics is not instance of PGraphics3D");		
	}
	
	@Override
  public void disableDepthTest() {
		pg().hint(PApplet.DISABLE_DEPTH_TEST);
	}
	
	@Override
	public void enableDepthTest() {
		pg().hint(PApplet.ENABLE_DEPTH_TEST);
	}
	
	@Override
	protected void drawSelectionHints() {
		for (Grabbable mg : terseHandler().globalGrabberList()) {
			if(mg instanceof InteractiveFrame) {
				InteractiveFrame iF = (InteractiveFrame) mg;// downcast needed
				if (!iF.isInCameraPath()) {
					Vec center = viewport().projectedCoordinatesOf(iF.position());
					if (grabsAnAgent(mg)) {
						pg().pushStyle();
					  //pg3d.stroke(mouseGrabberOnSelectionHintColor());
						pg().stroke(pg().color(0, 255, 0));
						pg().strokeWeight(2);
						drawShooterTarget(center, (iF.grabsInputThreshold() + 1));
						pg().popStyle();					
					}
					else {						
						pg().pushStyle();
					  //pg3d.stroke(mouseGrabberOffSelectionHintColor());
						pg().stroke(pg().color(240, 240, 240));
						pg().strokeWeight(1);
						drawShooterTarget(center, iF.grabsInputThreshold());
						pg().popStyle();
					}
				}
			}
		}
	}

	@Override
	protected void drawViewportPathSelectionHints() {
		for (Grabbable mg : terseHandler().globalGrabberList()) {
			if(mg instanceof InteractiveFrame) {
				InteractiveFrame iF = (InteractiveFrame) mg;// downcast needed
				if (iF.isInCameraPath()) {
					Vec center = viewport().projectedCoordinatesOf(iF.position());
					if (grabsAnAgent(mg)) {
						pg().pushStyle();						
					  //pg3d.stroke(mouseGrabberCameraPathOnSelectionHintColor());
						pg().stroke(pg().color(0, 255, 255));
						pg().strokeWeight(2);
						drawShooterTarget(center, (iF.grabsInputThreshold() + 1));
						pg().popStyle();
					}
					else {
						pg().pushStyle();
					  //pg3d.stroke(mouseGrabberCameraPathOffSelectionHintColor());
						pg().stroke(pg().color(255, 255, 0));
						pg().strokeWeight(1);
						drawShooterTarget(center, iF.grabsInputThreshold());
						pg().popStyle();
					}
				}
			}
		}
	}	
	
	@Override
	public int width() {
		return pg().width;
	}

	@Override
	public int height() {
		return pg().height;
	}			

	// 8. Keyboard customization

	/**
	 * Parses the sketch to find if any KeyXxxx method has been implemented. If
	 * this is the case, print a warning message telling the user what to do to
	 * avoid possible conflicts with proscene.
	 * <p>
	 * The methods sought are: {@code keyPressed}, {@code keyReleased}, and
	 * {@code keyTyped}.
	 */
	/**
	//TODO decide this:
	protected void parseKeyXxxxMethods() {
		boolean foundKP = true;
		boolean foundKR = true;
		boolean foundKT = true;

		try {
			parent.getClass().getDeclaredMethod("keyPressed");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundKP = false;
		} catch (NoSuchMethodException e) {
			foundKP = false;
		}

		try {
			parent.getClass().getDeclaredMethod("keyReleased");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundKR = false;
		} catch (NoSuchMethodException e) {
			foundKR = false;
		}

		try {
			parent.getClass().getDeclaredMethod("keyTyped");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundKT = false;
		} catch (NoSuchMethodException e) {
			foundKT = false;
		}

		if ( (foundKP || foundKR || foundKT) && keyboardIsHandled() ) {
			// if( (foundKP || foundKR || foundKT) &&
			// (!parent.getClass().getName().equals("remixlab.proscene.Viewer")) ) {
			PApplet.println("Warning: it seems that you have implemented some KeyXxxxMethod in your sketch. You may temporarily disable proscene " +
					"keyboard handling with Scene.disableKeyboardHandling() (you can re-enable it later with Scene.enableKeyboardHandling()).");
		}
	}
	*/
	
	/**
	 * Displays the {@link #info()} bindings.
	 * 
	 * @param onConsole if this flag is true displays the help on console.
	 * Otherwise displays it on the applet
	 * 
	 * @see #info()
	 */
	@Override
	public void displayInfo(boolean onConsole) {
		if (onConsole)
		//PApplet.println(info());
			System.out.println(info());
		else { //on applet
			pg().textFont(parent.createFont("Arial", 12));
			beginScreenDrawing();
			pg().fill(0,255,0);
			pg().textLeading(20);
			pg().text(info(), 10, 10, (pg().width-20), (pg().height-20));
			endScreenDrawing();
		}
	}	

	// 9. Mouse customization

	/**
	 * Parses the sketch to find if any mouseXxxx method has been implemented. If
	 * this is the case, print a warning message telling the user what to do to
	 * avoid possible conflicts with proscene.
	 * <p>
	 * The methods sought are: {@code mouseDragged}, {@code mouseMoved}, {@code
	 * mouseReleased}, {@code mousePressed}, and {@code mouseClicked}.
	 */
	/**
	protected void parseMouseXxxxMethods() {
		boolean foundMD = true;
		boolean foundMM = true;
		boolean foundMR = true;
		boolean foundMP = true;
		boolean foundMC = true;

		try {
			parent.getClass().getDeclaredMethod("mouseDragged");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundMD = false;
		} catch (NoSuchMethodException e) {
			foundMD = false;
		}

		try {
			parent.getClass().getDeclaredMethod("mouseMoved");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundMM = false;
		} catch (NoSuchMethodException e) {
			foundMM = false;
		}

		try {
			parent.getClass().getDeclaredMethod("mouseReleased");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundMR = false;
		} catch (NoSuchMethodException e) {
			foundMR = false;
		}

		try {
			parent.getClass().getDeclaredMethod("mousePressed");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundMP = false;
		} catch (NoSuchMethodException e) {
			foundMP = false;
		}

		try {
			parent.getClass().getDeclaredMethod("mouseClicked");
		} catch (SecurityException e) {
			e.printStackTrace();
			foundMC = false;
		} catch (NoSuchMethodException e) {
			foundMC = false;
		}

		if ( (foundMD || foundMM || foundMR || foundMP || foundMC) && mouseIsHandled() ) {			
			PApplet.println("Warning: it seems that you have implemented some mouseXxxxMethod in your sketch. You may temporarily disable proscene " +
			"mouse handling with Scene.disableMouseHandling() (you can re-enable it later with Scene.enableMouseHandling()).");
		}
	}
	*/

	// 10. Draw method registration

	/**
	 * Attempt to add a 'draw' handler method to the Scene. The default event
	 * handler is a method that returns void and has one single Scene parameter.
	 * 
	 * @param obj
	 *          the object to handle the event
	 * @param methodName
	 *          the method to execute in the object handler class
	 * 
	 * @see #removeDrawHandler()
	 */
	public void addDrawHandler(Object obj, String methodName) {
		try {
			drawHandlerMethod = obj.getClass().getMethod(methodName, new Class[] { Scene.class });
			drawHandlerObject = obj;
			drawHandlerMethodName = methodName;
		} catch (Exception e) {
			  PApplet.println("Something went wrong when registering your " + methodName + " method");
			  e.printStackTrace();
		}
	}

	/**
	 * Unregisters the 'draw' handler method (if any has previously been added to
	 * the Scene).
	 * 
	 * @see #addDrawHandler(Object, String)
	 */
	public void removeDrawHandler() {
		drawHandlerMethod = null;
		drawHandlerObject = null;
		drawHandlerMethodName = null;
	}

	/**
	 * Returns {@code true} if the user has registered a 'draw' handler method to
	 * the Scene and {@code false} otherwise.
	 */
	public boolean hasRegisteredDrawHandler() {
		if (drawHandlerMethodName == null)
			return false;
		return true;
	}
	
	// 11. Animation	
  
  /**
	 * Internal use.
	 * <p>
	 * Calls the animation handler. Calls {@link #animate()} if there's no such a handler. Sets
	 * the value of {@link #animatedFrameWasTriggered} to {@code true} or {@code false}
	 * depending on whether or not an animation event was triggered during this drawing frame
	 * (useful to notify the outside world when an animation event occurs). 
	 * 
	 * @see #animationPeriod()
	 * @see #startAnimation()
	 */
	@Override
	public boolean externalAnimation() {
		if (animateHandlerObject != null) {
			try {
				animateHandlerMethod.invoke(animateHandlerObject, new Object[] { this });
				return true;
			} catch (Exception e) {
				PApplet.println("Something went wrong when invoking your " + animateHandlerMethodName + " method");
				e.printStackTrace();
			}
		}
		return false;
	}
	
	/**
	 * Attempt to add an 'animation' handler method to the Scene. The default event
	 * handler is a method that returns void and has one single Scene parameter.
	 * 
	 * @param obj
	 *          the object to handle the event
	 * @param methodName
	 *          the method to execute in the object handler class
	 * 
	 * @see #animate()
	 */
	public void addAnimationHandler(Object obj, String methodName) {
		try {
			animateHandlerMethod = obj.getClass().getMethod(methodName, new Class[] { Scene.class });
			animateHandlerObject = obj;
			animateHandlerMethodName = methodName;
		} catch (Exception e) {
			  PApplet.println("Something went wrong when registering your " + methodName + " method");
			  e.printStackTrace();
		}
	}

	/**
	 * Unregisters the 'animation' handler method (if any has previously been added to
	 * the Scene).
	 * 
	 * @see #addAnimationHandler(Object, String)
	 */
	public void removeAnimationHandler() {
		animateHandlerMethod = null;
		animateHandlerObject = null;
		animateHandlerMethodName = null;
	}

	/**
	 * Returns {@code true} if the user has registered an 'animation' handler method to
	 * the Scene and {@code false} otherwise.
	 */
	public boolean hasRegisteredAnimationHandler() {
		if (animateHandlerMethodName == null)
			return false;
		return true;
	}
	
	//
	
	/**
	 * Returns the coordinates of the 3D point located at {@code pixel} (x,y) on
	 * screen.
	 */
	@Override
	protected Camera.WorldPoint pointUnderPixel(Point pixel) {
		float[] depth = new float[1];
		PGL pgl = pggl().beginPGL();
		pgl.readPixels(pixel.x, (camera().screenHeight() - pixel.y), 1, 1, PGL.DEPTH_COMPONENT, PGL.FLOAT, FloatBuffer.wrap(depth));		
		pggl().endPGL();		
		Vec point = new Vec(pixel.x, pixel.y, depth[0]);		
		point = camera().unprojectedCoordinatesOf(point);
		return camera().new WorldPoint(point, (depth[0] < 1.0f));
	}	
	
	/**
	//hack to make grabbers properly work
	public void mouseEvent(processing.event.MouseEvent e) {
		if( e.getAction() == processing.event.MouseEvent.RELEASE ) {
			for (Grabbable mg : deviceGrabberPool()) {
				if(mg instanceof InteractiveFrame)
					((InteractiveFrame)mg).keepsGrabbingCursor = false;
				else
					if(mg instanceof Grabber)
					((Grabber)mg).keepsGrabbingCursor = false;
			}			
		}
	}
	*/
}