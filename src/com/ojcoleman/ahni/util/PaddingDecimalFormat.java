package com.ojcoleman.ahni.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;

public class PaddingDecimalFormat extends DecimalFormat {
    private int minimumLength;
    
    /**
     * Creates a PaddingDecimalFormat using the given pattern and minimum minimumLength and the symbols for the default locale.
     */
    public PaddingDecimalFormat(String pattern, int minLength) {
    	super(pattern);
    	minimumLength = minLength;
    }

    /**
     * Creates a PaddingDecimalFormat using the given pattern, symbols and minimum minimumLength.
     */
    public PaddingDecimalFormat(String pattern, DecimalFormatSymbols symbols, int minLength) {
    	super(pattern, symbols);
    	minimumLength = minLength;
    }
    
    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
    	int initLength = toAppendTo.length();
    	super.format(number, toAppendTo, pos);
    	return pad(toAppendTo, initLength);
    }
    
    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
    	int initLength = toAppendTo.length();
    	super.format(number, toAppendTo, pos);
    	return pad(toAppendTo, initLength);
    }
    
    private StringBuffer pad(StringBuffer toAppendTo, int initLength) {
    	int numLength = toAppendTo.length() - initLength;
    	int padLength = minimumLength - numLength;
    	if (padLength > 0) {
    		StringBuffer pad = new StringBuffer(padLength);
	    	for(int i = 0; i < padLength; i++) {
	            pad.append(' ');
	        }
	    	toAppendTo.insert(initLength, pad);
    	}
        return toAppendTo;
    }
    
    public static void main(String[] args) {
    	PaddingDecimalFormat intFormat = new PaddingDecimalFormat("#", 6);
    	for (int i = 0; i < 20; i++) {
    		System.out.println(intFormat.format(i) + intFormat.format(i*i*i));
    	}
    }
}