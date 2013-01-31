package com.ojcoleman.ahni.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DecimalFormat;

public class NiceWriter extends BufferedWriter {
	private DecimalFormat nf;
	
	public NiceWriter(Writer out) {
		super(out);
		nf = new DecimalFormat("0.00");
	}
	
	public NiceWriter(Writer out, String numberFormat) {
		super(out);
		nf = new DecimalFormat(numberFormat);
	}
	
	public NiceWriter put(String a) throws IOException {
		write(a);
		return this;
	}
	public NiceWriter put(Object a) throws IOException {
		write(a.toString());
		return this;
	}
	public NiceWriter put(float a) throws IOException {
		write(nf.format(a));
		return this;
	}
	public NiceWriter put(double a) throws IOException {
		write(nf.format(a));
		return this;
	}
	public NiceWriter put(byte a) throws IOException {
		write("" + a);
		return this;
	}
	public NiceWriter put(short a) throws IOException {
		write("" + a);
		return this;
	}
	public NiceWriter put(int a) throws IOException {
		write("" + a);
		return this;
	}
	public NiceWriter put(long a) throws IOException {
		write("" + a);
		return this;
	}
	
	public NiceWriter put(double[] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(", ").put(a[i]);
		}
		put("]");
		return this;
	}
	public NiceWriter put(int[] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(", ").put(a[i]);
		}
		put("]");
		return this;
	}
	public NiceWriter put(byte[] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(", ").put(a[i]);
		}
		put("]");
		return this;
	}
	public NiceWriter put(boolean[] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put(a[0] ? 1 : 0);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(", ").put(a[i] ? 1 : 0);
		}
		put("]");
		return this;
	}

	public NiceWriter put(double[][] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put("\n\t").put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(",\n\t").put(a[i]);
		}
		put("]");
		return this;
	}
	
	public NiceWriter put(int[][] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put("\n\t").put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(",\n\t").put(a[i]);
		}
		put("]");
		return this;
	}
	
	public NiceWriter put(byte[][] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put("\n\t").put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(",\n\t").put(a[i]);
		}
		put("]");
		return this;
	}
	
	public NiceWriter put(boolean[][] a) throws IOException {
		put("[");
		if (a.length > 0) {
			put("\n\t").put(a[0]);
		}
		for (int i = 1; i < a.length; i++ ) {
			put(",\n\t").put(a[i]);
		}
		put("]");
		return this;
	}
}
