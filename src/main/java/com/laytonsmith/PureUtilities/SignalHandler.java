
package com.laytonsmith.PureUtilities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import sun.misc.Signal;

/**
 * Adds a generic way to handle signals, if the VM was started with the -Xrs option.
 */
public class SignalHandler {
	
	private static final Map<SignalType, SignalCallback> handlers = new HashMap<>();
	
	private static final Set<SignalType> setup = new HashSet<>();
	
	/**
	 * Registers a new signal handler, and returns the last one 
	 * @param type
	 * @param handler
	 * @return 
	 */
	public static SignalCallback addHandler(final SignalType type, SignalCallback handler){
		SignalCallback last = null;
		if(handlers.containsKey(type)){
			last = handlers.get(type);
		}
		handlers.put(type, handler);
		if(!setup.contains(type)){
			sun.misc.Signal.handle(new sun.misc.Signal(type.getSignalName()), new sun.misc.SignalHandler() {

				@Override
				public void handle(Signal sig) {
					boolean handled = handlers.get(type).handle(type);
					if(!handled){
						if(type.getDefaultAction() == SignalType.DefaultAction.IGNORE){
							sun.misc.SignalHandler.SIG_IGN.handle(sig);
						} else {
							sun.misc.SignalHandler.SIG_DFL.handle(sig);
						}
					}
				}
			});
			setup.add(type);
		}
		return last;
	}
	
	/**
	 * Raises the specified signal in the process.
	 * @param type 
	 */
	public static void raise(SignalType type){
		sun.misc.Signal.raise(new sun.misc.Signal(type.getSignalName()));
	}
	
	public static interface SignalCallback {
		/**
		 * When the signal this was registered with occurs, this method is called.
		 * @param type The type that activated this.
		 * @return If the signal should be ignored, return true. If false is returned,
		 * the default action will occur.
		 */
		boolean handle(SignalType type);
	}
	
}