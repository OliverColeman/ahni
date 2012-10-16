/*
 * Copyright (C) 2004 Derek James and Philip Tucker
 * 
 * This file is part of ANJI (Another NEAT Java Implementation).
 * 
 * ANJI is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 * 
 * created by Philip Tucker on Jun 4, 2003
 */
package com.anji.nn.activationfunction;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerated type representing flavors of neurons: input, output, hidden. Values returned in
 * <code>toString()</code> correspond to values in <a href="http://nevt.sourceforge.net/">NEVT
 * </a> XML data model.
 * 
 * @author Philip Tucker
 */
public class ActivationFunctionType {

    /**
     * for hibernate
     */
    private Long id;

    private String name = null;

    private static Map types = null;

    private static ActivationFunctionType[] typesArray = null;

    /**
     * linear
     */
    public final static ActivationFunctionType LINEAR = new ActivationFunctionType( "linear" );
    /**
     * negated linear
     */
    public final static ActivationFunctionType NEGATED_LINEAR = new ActivationFunctionType( "negated-linear" );

    /**
     * sigmoid
     */
    public final static ActivationFunctionType SIGMOID = new ActivationFunctionType( "sigmoid" );

    /**
     * sigmoid
     */
    public final static ActivationFunctionType BIPOLAR_SIGMOID = new ActivationFunctionType( "sigmoid-bipolar" );

    /**
     * tanh
     */
    public final static ActivationFunctionType TANH = new ActivationFunctionType( "tanh" );

    /**
     * tanh cubic
     */
    public final static ActivationFunctionType TANH_CUBIC = new ActivationFunctionType("tanh-cubic");

    /**
     * clamped linear
     */
    public final static ActivationFunctionType CLAMPED_LINEAR = new ActivationFunctionType("clamped-linear" );

    /**
     * signed clamped linear
     */
    public final static ActivationFunctionType SIGNED_CLAMPED_LINEAR = new ActivationFunctionType("signed-clamped-linear" );

    public final static ActivationFunctionType GAUSSIAN = new ActivationFunctionType("gaussian" );

    public final static ActivationFunctionType SINE = new ActivationFunctionType("sine" );

    public final static ActivationFunctionType COSINE = new ActivationFunctionType("cosine" );

    public final static ActivationFunctionType ABSOLUTE = new ActivationFunctionType("absolute" );

    public final static ActivationFunctionType CLAMPED_ABSOLUTE = new ActivationFunctionType("clamped-absolute" );

    public final static ActivationFunctionType STEP = new ActivationFunctionType("step" );

    public final static ActivationFunctionType CONVERT_TO_SIGNED = new ActivationFunctionType("sign");

    public final static ActivationFunctionType INVERSE_ABS = new ActivationFunctionType("inverse-abs");
    
    public final static ActivationFunctionType DIVIDE = new ActivationFunctionType("divide");
    
    

    /**
     * random (any of the above)
     */
    public final static ActivationFunctionType RANDOM = new ActivationFunctionType("random" );

    /**
     * @param newName id of type
     */
    private ActivationFunctionType( String newName ) {
        name = newName;
    }

    /**
     * @param name id of type
     * @return <code>ActivationFunctionType</code> enumerated type corresponding to
     * <code>name</code>
     */
    public static ActivationFunctionType valueOf( String name ) {
        if ( types == null ) {
            types = new HashMap<String, ActivationFunctionType>();
            
            types.put( ActivationFunctionType.LINEAR.toString(), ActivationFunctionType.LINEAR );
            types.put( ActivationFunctionType.CLAMPED_LINEAR.toString(), ActivationFunctionType.CLAMPED_LINEAR );
            types.put( ActivationFunctionType.NEGATED_LINEAR.toString(), ActivationFunctionType.NEGATED_LINEAR );
            types.put( ActivationFunctionType.SIGNED_CLAMPED_LINEAR.toString(), ActivationFunctionType.SIGNED_CLAMPED_LINEAR );
            types.put( ActivationFunctionType.ABSOLUTE.toString(), ActivationFunctionType.ABSOLUTE );
            types.put( ActivationFunctionType.CLAMPED_ABSOLUTE.toString(), ActivationFunctionType.CLAMPED_ABSOLUTE );
            types.put( ActivationFunctionType.STEP.toString(), ActivationFunctionType.STEP );
            types.put( ActivationFunctionType.CONVERT_TO_SIGNED.toString(), ActivationFunctionType.CONVERT_TO_SIGNED );
            types.put( ActivationFunctionType.INVERSE_ABS.toString(), ActivationFunctionType.INVERSE_ABS );
            types.put( ActivationFunctionType.DIVIDE.toString(), ActivationFunctionType.DIVIDE );
            types.put( ActivationFunctionType.SIGMOID.toString(), ActivationFunctionType.SIGMOID );
            types.put( ActivationFunctionType.BIPOLAR_SIGMOID.toString(), ActivationFunctionType.BIPOLAR_SIGMOID );
            types.put( ActivationFunctionType.GAUSSIAN.toString(), ActivationFunctionType.GAUSSIAN );
            types.put( ActivationFunctionType.SINE.toString(), ActivationFunctionType.SINE );
            types.put( ActivationFunctionType.COSINE.toString(), ActivationFunctionType.COSINE );
            types.put( ActivationFunctionType.TANH.toString(), ActivationFunctionType.TANH );
            types.put( ActivationFunctionType.TANH_CUBIC.toString(), ActivationFunctionType.TANH_CUBIC );
            
            typesArray = new ActivationFunctionType[types.size()];
            typesArray = (ActivationFunctionType[]) types.values().toArray(typesArray);

            //don't include the random "metatype" in the list of types
            types.put( ActivationFunctionType.RANDOM.toString(), ActivationFunctionType.RANDOM );
        }
        return (ActivationFunctionType) types.get( name );
    }

    /**
     * @see Object#equals(java.lang.Object)
     */
    public boolean equals( Object o ) {
        return ( this == o );
    }

    /**
     * @see Object#toString()
     */
    public String toString() {
        return name;
    }

    /**
     * define this so objects may be used in hash tables
     *
     * @see Object#hashCode()
     */
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * for hibernate
     * @return unique id
     */
    private Long getId() {
        return id;
    }

    /**
     * for hibernate
     * @param aId
     */
    private void setId( Long aId ) {
        id = aId;
    }


    public static ActivationFunctionType[] getTypes() {
        return typesArray;
    }

}
