/*
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christianfries.com.
 *
 * Created on 09.02.2018
 */

package net.finmath.experimental.model;

/**
 * Interface for a model descriptor.
 * For a description of the general concept see <a href="http://finmath.net/finmath-lib/concepts/separationofproductandmodel">http://finmath.net/finmath-lib/concepts/separationofproductandmodel</a>.
 * 
 * @author Christian Fries
 */
public interface ModelDescriptor {
	
	/**
	 * Return the version of the model description.
	 * 
	 * @return Version number.
	 */
	Integer version();
	
	/**
	 * Return the name of the model represented by this descriptor.
	 * 
	 * @return Name of the model.
	 */
	String name();
}