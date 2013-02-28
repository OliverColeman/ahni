package com.ojcoleman.ahni.transcriber;

import java.util.Map;

import org.jgapcustomised.Chromosome;

import com.anji.integration.Activator;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;

public abstract class TranscriberAdaptor<T extends Activator> implements Transcriber<T> {
	/**
	 * Sub-classes can override this method to convert the genotype to a phenotype, using an existing substrate object
	 * to allow performance gains by avoiding destroying and creating large objects or arrays, and optionally
	 * providing additional options.
	 * 
	 * @param c chromosome to transcribe
	 * @param substrate An existing phenotype substrate to reuse
	 * @param A set of transcriver specific options. May be null. 
	 * @return phenotype
	 * @throws TranscriberException
	 */
	public T transcribe(Chromosome c, T substrate, Map<String, Object> options) throws TranscriberException {
		return transcribe(c, substrate);
	}
}
