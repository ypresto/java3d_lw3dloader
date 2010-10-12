/*
 * $RCSfile$
 *
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF
 * USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR
 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL,
 * CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND
 * REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
 * INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 *
 * $Revision: 150 $
 * $Date: 2007-02-10 02:20:46 +0900 (土, 10 2 2007) $
 * $State$
 */

package com.sun.j3d.utils.behaviors.sensor ;

import java.util.Iterator ;
import java.util.List ;
import java.util.ArrayList ;
import javax.media.j3d.Sensor ;
import javax.media.j3d.Transform3D ;
import com.sun.j3d.utils.timer.J3DTimer ;

/**
 * This class works in conjunction with the <code>SensorButtonListener</code>
 * and <code>SensorReadListener</code> interfaces to support an event-driven
 * model of sensor interaction.  Java 3D defines sensors as delivering
 * continuous input data which must be polled to retrieve their values, but in
 * practice it is often convenient to structure application code to respond to
 * events such as button state transitions.
 * <p>
 * Listeners registered with this class are invoked when its
 * <code>dispatchEvents</code> method is called.  This is usually called from
 * the <code>processStimulus</code> method of a <code>Behavior</code>, but may
 * also be called directly from the <code>pollAndProcessInput</code> method of
 * an event-driven implementation of <code>InputDevice</code>.  In either case
 * the device is still polled by the Java 3D input device scheduling thread to
 * get its current values; however, in the former, <code>dispatchEvents</code>
 * is called from the behavior scheduler thread regardless of whether any new
 * events are available, while in the latter, the <code>InputDevice</code>
 * implementation may choose to call <code>dispatchEvents</code> only if new
 * events are actually generated.
 * <p>
 * Button events are generated by changes in sensor button state, from pressed
 * to released and vice versa.  Button state changes are examined with each
 * invocation of the <code>dispatchEvents</code> method.  Events are
 * distributed to interested parties through the button listener interface
 * using the <code>pressed</code> and <code>released</code> callbacks.
 * <p>
 * The <code>dragged</code> method is not necessarily called in response to a
 * motion event generated by a sensor.  <code>dispatchEvents</code> will call
 * <code>dragged</code> whenever any button assigned to the listener is down
 * and has not changed state since the last time it was called.  If
 * <code>dispatchEvents</code> is called in the <code>processStimulus</code>
 * of a <code>Behavior</code>, then <code>dragged</code> may be called even if
 * the sensor value has not changed.  This is as a consequence of the core
 * Java 3D API definition of sensors as continuous devices.
 * <p>
 * Like <code>dragged</code>, the <code>read</code> method of
 * <code>SensorReadListener</code> is not necessarily invoked in response to a
 * real event.  It is called by <code>dispatchEvents</code> whenever a button
 * listener has not been called for that sensor.  This usually means that no
 * buttons are down, but clients are free to leave a button listener null, or
 * to explicitly bind a null button listener to a button so that button's
 * events are ignored.  The sensor value has not necessarily changed since the
 * last <code>read</code> callback.
 * <p>
 * A <i>mutual exclusion</i> policy can be applied between button
 * listeners when they are grouped in an array mapped to the sensor's
 * buttons.  If multiple sensor buttons are held down at the same time,
 * then a listener in the array is invoked only for the button that was
 * depressed first.  The <code>read</code> callback is separated from the
 * <code>pressed</code>, <code>released</code>, and <code>dragged</code>
 * callbacks in a separate interface in order to support this policy.
 * <p>
 * The events passed to the listeners are <i>ephemeral</i>; they are only
 * valid until the listener has returned.  This is done to avoid
 * allocating large numbers of mostly temporary objects, especially for
 * behaviors that wake up every frame.  If a listener needs to retain the
 * event it must be copied using the <code>SensorEvent(SensorEvent)</code>
 * constructor.
 * <p>
 * It is safe to add and remove listeners in response to a callback.
 * 
 * @see SensorEvent
 * @see SensorButtonListener
 * @see SensorReadListener
 * @since Java 3D 1.3
 */
public class SensorEventAgent {
    private long t0 = 0 ;
    private Object source = null ;
    private SensorEvent e = new SensorEvent() ;

    // List of SensorBinding objects and corresponding array.
    private List bindingsList = new ArrayList() ;
    private SensorBinding[] bindings = new SensorBinding[0] ;

    // Indicates that lists must be converted to arrays.  Need to do this
    // to allow listeners to add and remove themselves or other listeners
    // safely during event dispatch.
    private boolean listsDirty = false ;

    /**
     * Create a <code>SensorEventAgent</code> to generate and dispatch
     * sensor events to registered listeners.
     * 
     * @param source reference to the originating object for inclusion in
     *  generated <code>SensorEvents</code>; intended to refer to the
     *  instantiating Behavior but may be any reference, or null
     */
    public SensorEventAgent(Object source) {
	this.source = source ;
    }

    /**
     * This class contains all the button and read listeners registered
     * with a sensor.
     */
    private static class SensorBinding {
	Sensor sensor = null ;
	int[] buttons = null ;
	Transform3D read = null ;

	// List of SensorButtonBinding objects and corresponding array.
	List buttonBindingsList = new ArrayList() ;
	SensorButtonBinding[] buttonBindings = new SensorButtonBinding[0] ;

	// List of SensorReadListener objects and corresponding array.
	List readBindingsList = new ArrayList() ;
	SensorReadListener[] readBindings = new SensorReadListener[0] ;

	SensorBinding(Sensor sensor) {
	    this.sensor = sensor ;
	    buttons = new int[sensor.getSensorButtonCount()] ;
	    read = new Transform3D() ;
	}

	void updateArrays() {
	    buttonBindings =
		(SensorButtonBinding[])buttonBindingsList.toArray
		(new SensorButtonBinding[buttonBindingsList.size()]) ;
	    readBindings =
		(SensorReadListener[])readBindingsList.toArray
		(new SensorReadListener[readBindingsList.size()]) ;
	}

	public String toString() {
	    String s = new String() ;
	    s = "sensor " + sensor + "\nbutton listener arrays:\n" ;
	    for (int i = 0 ; i < buttonBindingsList.size() ; i++)
		s = s + ((SensorButtonBinding)buttonBindingsList.get(i)) ;
	    s = s + "read listeners:\n" ;
	    for (int i = 0 ; i < readBindingsList.size() ; i++)
		s = s + "  " +
		    ((SensorReadListener)readBindingsList.get(i)) + "\n" ;
	    return s ;
	}
    }       

    /**
     * This class contains an array of SensorButtonListener
     * implementations, one for each sensor button.  This array is used to
     * support a mutual exclusion callback policy.  There may be multiple
     * instances of this class associated with a single sensor.
     */
    private static class SensorButtonBinding {
	int buttonsHandled = 0 ;
	boolean[] prevButtons = null ;
	boolean multiButton = false ;
	SensorButtonListener[] listeners = null ;

	SensorButtonBinding(SensorButtonListener[] listeners,
			    boolean multiButtonEnable) {

	    prevButtons = new boolean[listeners.length] ;
	    this.listeners = new SensorButtonListener[listeners.length] ;

	    for (int i = 0 ; i < listeners.length ; i++) {
		prevButtons[i] = false ;
		this.listeners[i] = listeners[i] ;
	    }

	    this.multiButton = multiButtonEnable ;
	}

	public String toString() {
	    String s = new String() ;
	    s = "  length " + listeners.length +
		", mutual exclusion " + (!multiButton) + "\n" ;
	    for (int i = 0 ; i < listeners.length ; i++)
		s = s + "    " +
		    (listeners[i] == null?
		     "null" : listeners[i].toString()) + "\n" ;
	    return s ;
	}
    }

    /**
     * Look up the sensor listeners bound to the given sensor.
     */
    private SensorBinding getSensorBinding(Sensor sensor) {
	for (int i = 0 ; i < bindingsList.size() ; i++) {
	    SensorBinding sb = (SensorBinding)bindingsList.get(i) ;
	    if (sb.sensor == sensor)
		return sb ;
	}
	return null ;
    }

    /**
     * Creates a binding of the specified sensor button to the given
     * <code>SensorButtonListener</code> implementation.
     * 
     * @param sensor the sensor with the button to be bound
     * @param button the index of the button to be bound on the specified
     *  sensor; may range from 0 to
     *  <code>(sensor.getSensorButtonCount() - 1)</code>
     * @param buttonListener the <code>SensorButtonListener</code>
     *  implementation that will be invoked for the sensor's button
     */
    public synchronized void addSensorButtonListener
	(Sensor sensor, int button, SensorButtonListener buttonListener) {

	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	if (button >= sensor.getSensorButtonCount())
	    throw new ArrayIndexOutOfBoundsException
		("\nbutton " + button + " >= sensor button count " +
		 sensor.getSensorButtonCount()) ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null) {
	    sb = new SensorBinding(sensor) ;
	    bindingsList.add(sb) ;
	}

	SensorButtonListener[] listeners =
	    new SensorButtonListener[sb.buttons.length] ;

	// Assign only the specified button; others remain null.
	listeners[button] = buttonListener ;
	SensorButtonBinding sbb =
	    new SensorButtonBinding(listeners, true) ;

	sb.buttonBindingsList.add(sbb) ;
	listsDirty = true ;
    }

    /**
     * Creates a binding from all the buttons on the specified sensor to
     * the given <code>SensorButtonListener</code> implementation.  If
     * multiple sensor buttons are held down at the same time, the press
     * and release callbacks are called for each button in the order that
     * they occur.  This allows actions to be bound to combinations of
     * button presses, but is also convenient for listeners that don't
     * care which button was pressed.
     * 
     * @param sensor the sensor to be bound
     * @param buttonListener the <code>SensorButtonListener</code>
     *  implementation that will be called for all button events
     */
    public synchronized void addSensorButtonListener
	(Sensor sensor, SensorButtonListener buttonListener) {

	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null) {
	    sb = new SensorBinding(sensor) ;
	    bindingsList.add(sb) ;
	}

	SensorButtonListener[] listeners =
	    new SensorButtonListener[sb.buttons.length] ;

	// All buttons are bound to the same listener.
	for (int i = 0 ; i < sb.buttons.length ; i++)
	    listeners[i] = buttonListener ;

	SensorButtonBinding sbb =
	    new SensorButtonBinding(listeners, true) ;

	sb.buttonBindingsList.add(sbb) ;
	listsDirty = true ;
    }

    /**
     * Creates a binding of the specified sensor to the given array of
     * <code>SensorButtonListener</code> implementations.  The array index
     * of the listener indicates the index of the sensor button to which
     * it will be bound.
     * <p>
     * This method enforces a <i>mutually exclusive</i> callback policy
     * among the listeners specified in the array.  If multiple sensor
     * buttons are held down at the same time, callbacks are invoked only
     * for the button that was depressed first.
     * 
     * @param sensor the sensor to be bound
     * @param buttonListeners array of implementations of
     *  <code>SensorButtonListener</code>; array entries may be null or
     *  duplicates but the array length must equal the sensor's button
     *  count
     */
    public synchronized void addSensorButtonListeners
	(Sensor sensor, SensorButtonListener[] buttonListeners) {

	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null) {
	    sb = new SensorBinding(sensor) ;
	    bindingsList.add(sb) ;
	}

	if (sb.buttons.length != buttonListeners.length)
	    throw new IllegalArgumentException
		("\nbuttonListeners length " + buttonListeners.length +
		 " must equal sensor button count " + sb.buttons.length) ;

	SensorButtonBinding sbb =
	    new SensorButtonBinding(buttonListeners, false) ;

	sb.buttonBindingsList.add(sbb) ;
	listsDirty = true ;
    }

    /**
     * Gets the <code>SensorButtonListener</code> implementations bound to
     * the given sensor and button.
     * 
     * @param sensor the sensor of interest
     * @param button the button of interest
     * @return array of <code>SensorButtonListener</code> implementations
     *  bound to the given sensor and button, or null
     */
    public SensorButtonListener[] getSensorButtonListeners(Sensor sensor,
							   int button) {
	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	if (button >= sensor.getSensorButtonCount())
	    throw new ArrayIndexOutOfBoundsException
		("\nbutton " + button + " >= sensor button count " +
		 sensor.getSensorButtonCount()) ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null)
	    return null ;

	ArrayList listeners = new ArrayList() ;
	for (int i = 0 ; i < sb.buttonBindingsList.size() ; i++) {
	    SensorButtonBinding sbb =
		(SensorButtonBinding)sb.buttonBindingsList.get(i) ;

	    if (sbb.listeners[button] != null)
		listeners.add(sbb.listeners[button]) ;
	}

	if (listeners.size() == 0)
	    return null ;
	else
	    return (SensorButtonListener[])listeners.toArray
		(new SensorButtonListener[listeners.size()]) ;
    }

    /**
     * Remove the SensorButtonListener from the given SensorBinding.
     */
    private void removeSensorButtonListener
	(SensorBinding sb, SensorButtonListener listener) {

	Iterator i = sb.buttonBindingsList.iterator() ;
	while (i.hasNext()) {
	    int instanceCount = 0 ;
	    SensorButtonBinding sbb = (SensorButtonBinding)i.next() ;

	    for (int j = 0 ; j < sbb.listeners.length ; j++) {
		if (sbb.listeners[j] == listener)
		    sbb.listeners[j] = null ;
		else if (sbb.listeners[j] != null)
		    instanceCount++ ;
	    }
	    if (instanceCount == 0) {
		i.remove() ;
	    }
	}
	listsDirty = true ;
    }

    /**
     * Remove the given <code>SensorButtonListener</code> binding from the
     * specified sensor.
     * 
     * @param sensor the sensor from which to remove the listener
     * @param listener the listener to be removed
     */
    public synchronized void removeSensorButtonListener
	(Sensor sensor, SensorButtonListener listener) {
        
	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null)
	    return ;

	removeSensorButtonListener(sb, listener) ;
	if (sb.buttonBindingsList.size() == 0 &&
	    sb.readBindingsList.size() == 0)
	    removeSensorBinding(sensor) ;

	listsDirty = true ;
    }

    /**
     * Remove the given <code>SensorButtonListener</code> from all sensors.
     * 
     * @param listener the listener to remove
     */
    public synchronized void removeSensorButtonListener
	(SensorButtonListener listener) {

	Iterator i = bindingsList.iterator() ;
	while (i.hasNext()) {
	    SensorBinding sb = (SensorBinding)i.next() ;
	    removeSensorButtonListener(sb, listener) ;

	    if (sb.buttonBindingsList.size() == 0 &&
		sb.readBindingsList.size() == 0) {
		i.remove() ;
	    }
	}
	listsDirty = true ;
    }

    /**
     * Creates a binding of the specified sensor to the given
     * <code>SensorReadListener</code>.  The read listener is invoked
     * every time <code>dispatchEvents</code> is called and a button
     * listener is <i>not</i> invoked.
     * 
     * @param sensor the sensor to be bound
     * @param readListener the <code>SensorReadListener</code>
     *  implementation
     */
    public synchronized void addSensorReadListener
	(Sensor sensor, SensorReadListener readListener) {

	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null) {
	    sb = new SensorBinding(sensor) ;
	    bindingsList.add(sb) ;
	}
	sb.readBindingsList.add(readListener) ;
	listsDirty = true ;
    }

    /**
     * Gets the <code>SensorReadListeners</code> bound to the specified
     * sensor.
     * 
     * @param sensor the sensor of interest
     * @return array of <code>SensorReadListeners</code> bound to the
     *  given sensor, or null
     */
    public SensorReadListener[] getSensorReadListeners(Sensor sensor) {
	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null)
	    return null ;
	else if (sb.readBindingsList.size() == 0)
	    return null ;
	else
	    return (SensorReadListener[])sb.readBindingsList.toArray
		(new SensorReadListener[sb.readBindingsList.size()]) ;
    }

    /**
     * Remove the SensorReadListener from the given SensorBinding.
     */
    private void removeSensorReadListener
	(SensorBinding sb, SensorReadListener listener) {

	Iterator i = sb.readBindingsList.iterator() ;
	while (i.hasNext()) {
	    if (((SensorReadListener)i.next()) == listener)
		i.remove() ;
	}
	listsDirty = true ;
    }

    /**
     * Remove the given <code>SensorReadListener</code> binding from the
     * specified sensor.
     * 
     * @param sensor the sensor from which to remove the listener
     * @param listener the listener to be removed
     */
    public synchronized void removeSensorReadListener
	(Sensor sensor, SensorReadListener listener) {
        
	if (sensor == null)
	    throw new NullPointerException("\nsensor is null") ;

	SensorBinding sb = getSensorBinding(sensor) ;
	if (sb == null)
	    return ;

	removeSensorReadListener(sb, listener) ;
	if (sb.buttonBindingsList.size() == 0 &&
	    sb.readBindingsList.size() == 0)
	    removeSensorBinding(sensor) ;

	listsDirty = true ;
    }

    /**
     * Remove the given <code>SensorReadListener</code> from all sensors.
     * 
     * @param listener the listener to remove
     */
    public synchronized void removeSensorReadListener
	(SensorReadListener listener) {

	Iterator i = bindingsList.iterator() ;
	while (i.hasNext()) {
	    SensorBinding sb = (SensorBinding)i.next() ;
	    removeSensorReadListener(sb, listener) ;

	    if (sb.buttonBindingsList.size() == 0 &&
		sb.readBindingsList.size() == 0) {
		i.remove() ;
	    }
	}
	listsDirty = true ;
    }

    /**
     * Remove all sensor listeners bound to the given sensor.
     */
    public synchronized void removeSensorBinding(Sensor sensor) {
	Iterator i = bindingsList.iterator() ;
	while (i.hasNext()) {
	    SensorBinding sb = (SensorBinding)i.next() ;
	    if (sb.sensor == sensor) {
		i.remove() ;
		break ;
	    }
	}
	listsDirty = true ;
    }

    /**
     * Returns an array of references to all sensors that have been bound
     * to listeners.
     * @return an array of sensors, or null if no sensors have been bound
     */
    public Sensor[] getSensors() {
	if (bindingsList.size() == 0)
	    return null ;

	Sensor[] s = new Sensor[bindingsList.size()] ;
	for (int i = 0 ; i < bindingsList.size() ; i++)
	    s[i] = ((SensorBinding)bindingsList.get(i)).sensor ;

	return s ;
    }

    /**
     * Copies binding lists to arrays for event dispatch.  This allows
     * listeners to add or remove themselves or other listeners safely.
     */ 
    private synchronized void updateArrays() {
	bindings = (SensorBinding[])bindingsList.toArray
	    (new SensorBinding[bindingsList.size()]) ;

	for (int i = 0 ; i < bindings.length ; i++) {
	    bindings[i].updateArrays() ;
	}
    }

    /**
     * Reads all sensor button state and dispatches events to registered
     * button and read listeners.  This method is intended to be called from
     * the <code>processStimulus</code> implementation of a
     * <code>Behavior</code> or the <code>pollAndProcessInput</code> method of
     * an event-driven implementation of <code>InputDevice</code>.
     */
    public void dispatchEvents() {
	long t1 = t0 ;
	t0 = J3DTimer.getValue() ;
        
	if (listsDirty) {
	    updateArrays() ;
	    listsDirty = false ;
	}

	// Loop through all sensor bindings.
	for (int k = 0 ; k < bindings.length ; k++) {
	    SensorBinding sb = bindings[k] ;
	    Sensor s = sb.sensor ;
	    Transform3D read = sb.read ;
	    int[] buttons = sb.buttons ;
	    int dragButton = 0 ;
	    boolean callReadListeners = true ;
	    boolean callDraggedListener = false ;

	    // Get this sensor's readings.
	    s.getRead(read) ;
	    s.lastButtons(buttons) ;

	    // Dispatch button listeners.
	    for (int j = 0 ; j < sb.buttonBindings.length ; j++) {
		SensorButtonBinding sbb = sb.buttonBindings[j] ;
		for (int i = 0 ; i < buttons.length ; i++) {
		    if (sbb.listeners[i] == null)
			continue ;

		    // Check for button release.
		    if (sbb.prevButtons[i]) {
			if (buttons[i] == 0) {
			    e.set(source, SensorEvent.RELEASED, s, read,
				  buttons, i, t0, t1) ;
			    sbb.listeners[i].released(e) ;
			    sbb.prevButtons[i] = false ;
			    sbb.buttonsHandled-- ;
			}
			else {
			    callDraggedListener = true ;
			    dragButton = i ;
			}
			callReadListeners = false ;
		    }
		    // Check for button press.
		    // Ignore multiple button presses if not enabled;
		    // otherwise, one listener is bound to all buttons.
		    else if (buttons[i] == 1) {
			if (sbb.buttonsHandled == 0 || sbb.multiButton) {
			    e.set(source, SensorEvent.PRESSED, s, read,
				  buttons, i, t0, t1) ;
			    sbb.listeners[i].pressed(e) ;
			    sbb.prevButtons[i] = true ;
			    sbb.buttonsHandled++ ;
			    callReadListeners = false ;
			}
		    }
		}
		if (callDraggedListener) {
		    // One drag event even if multiple buttons down.
		    // Called after all pressed() and released() calls.
		    e.set(source, SensorEvent.DRAGGED, s, read, buttons,
			  SensorEvent.NOBUTTON, t0, t1) ;
		    sbb.listeners[dragButton].dragged(e) ;
		}
	    }
	    // Dispatch read listeners.
	    if (callReadListeners) {
		e.set(source, SensorEvent.READ, s, read,
		      buttons, SensorEvent.NOBUTTON, t0, t1) ;
		for (int r = 0 ; r < sb.readBindings.length ; r++) {
		    sb.readBindings[r].read(e) ;
		}
	    }
	}
    }

    public String toString() {
	String s = "SensorEventAgent@" + Integer.toHexString(hashCode()) ;
	s += "\nsensor bindings:\n\n" ;
	for (int i = 0 ; i < bindingsList.size() ; i++) {
	    s += ((SensorBinding)bindingsList.get(i)).toString() + "\n" ;
	}
	return s ;
    }
}

