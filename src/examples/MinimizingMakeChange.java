/*
 * 
 * Copyright 2001-2003 Neil Rotstan
 * 
 * 
 * 
 * This file is part of JGAP.
 * 
 * 
 * 
 * JGAP is free software; you can redistribute it and/or modify
 * 
 * it under the terms of the GNU Lesser Public License as published by
 * 
 * the Free Software Foundation; either version 2.1 of the License, or
 * 
 * (at your option) any later version.
 * 
 * 
 * 
 * JGAP is distributed in the hope that it will be useful,
 * 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * 
 * GNU Lesser Public License for more details.
 * 
 * 
 * 
 * You should have received a copy of the GNU Lesser Public License
 * 
 * along with JGAP; if not, write to the Free Software Foundation, Inc.,
 * 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *  
 */

package examples;

import java.util.ArrayList;
import java.util.List;

import org.jgap.Chromosome;
import org.jgap.ChromosomeMaterial;
import org.jgap.Configuration;
import org.jgap.FitnessFunction;
import org.jgap.Genotype;
import org.jgap.impl.DefaultConfiguration;
import org.jgap.impl.IntegerAllele;

/**
 * 
 * This class provides an implementation of the classic "Make change" problem
 * 
 * using a genetic algorithm. The goal of the problem is to provide a
 * 
 * specified amount of change (from a cash purchase) in the fewest coins
 * 
 * possible. This example implementation uses American currency (quarters,
 * 
 * dimes, nickels, and pennies).
 * 
 * <p>
 * 
 * This example may be seen as somewhat significant because it demonstrates
 * 
 * the use of a genetic algorithm in a less-than-optimal problem space.
 * 
 * The genetic algorithm does best when there is a smooth slope of fitness
 * 
 * over the problem space towards the optimum solution. This problem exhibits
 * 
 * a more choppy space with more local optima. However, as can be seen from
 * 
 * running this example, the genetic algorithm still will get the correct
 * 
 * answer virtually everytime.
 *  
 */

public class MinimizingMakeChange

{

/**
 * 
 * The total number of times we'll let the population evolve.
 *  
 */

private static final int MAX_ALLOWED_EVOLUTIONS = 50;

/**
 * 
 * Executes the genetic algorithm to determine the minimum number of
 * 
 * coins necessary to make up the given target amount of change. The
 * 
 * solution will then be written to System.out.
 * 
 * 
 * 
 * @param a_targetChangeAmount The target amount of change for which this
 * 
 * method is attempting to produce the minimum
 * 
 * number of coins.
 * 
 * 
 * 
 * @throws Exception
 *  
 */

public static void makeChangeForAmount( int a_targetChangeAmount ) throws Exception {
	// Start with a DefaultConfiguration, which comes setup with the
	// most common settings.
	// -------------------------------------------------------------
	Configuration conf = new DefaultConfiguration();

	// Set the fitness function we want to use, which is our
	// MinimizingMakeChangeFitnessFunction. We construct it with
	// the target amount of change passed in to this method.
	// ---------------------------------------------------------
	FitnessFunction myFunc = new MinimizingMakeChangeFitnessFunction( a_targetChangeAmount );

	conf.setFitnessFunction( myFunc );

	// Now we need to tell the Configuration object how we want our
	// Chromosomes to be setup. We do that by actually creating a
	// sample Chromosome and then setting it on the Configuration
	// object. As mentioned earlier, we want our Chromosomes to each
	// have four genes, one for each of the coin types. We want the
	// values (alleles) of those genes to be integers, which represent
	// how many coins of that type we have. We therefore use the
	// IntegerGene class to represent each of the genes. That class
	// also lets us specify a lower and upper bound, which we set
	// to sensible values for each coin type.
	// --------------------------------------------------------------
	List sampleAlleles = new ArrayList( 4 );

	sampleAlleles.add( new IntegerAllele( 0, 3 ) ); // Quarters
	sampleAlleles.add( new IntegerAllele( 0, 2 ) ); // Dimes
	sampleAlleles.add( new IntegerAllele( 0, 1 ) ); // Nickels
	sampleAlleles.add( new IntegerAllele( 0, 4 ) ); // Pennies

	ChromosomeMaterial sample = new ChromosomeMaterial( sampleAlleles );
	conf.setSampleChromosomeMaterial( sample );

	// Finally, we need to tell the Configuration object how many

	// Chromosomes we want in our population. The more Chromosomes,

	// the larger number of potential solutions (which is good for

	// finding the answer), but the longer it will take to evolve

	// the population (which could be seen as bad). We'll just set

	// the population size to 500 here.

	// ------------------------------------------------------------

	conf.setPopulationSize( 500 );

	// Create random initial population of Chromosomes.

	// ------------------------------------------------

	Genotype population = Genotype.randomInitialGenotype(null, conf );

	// Evolve the population. Since we don't know what the best answer

	// is going to be, we just evolve the max number of times.

	// ---------------------------------------------------------------

	for ( int i = 0; i < MAX_ALLOWED_EVOLUTIONS; i++ )

	{

		population.evolve();

	}

	// Display the best solution we found.

	// -----------------------------------

	Chromosome bestSolutionSoFar = population.getFittestChromosome();

	System.out.println( "The best solution contained the following: " );

	System.out.println( "\t" +

	MinimizingMakeChangeFitnessFunction.getNumberOfCoinsAtGene(

	bestSolutionSoFar, 0 ) + " quarters." );

	System.out.println( "\t" +

	MinimizingMakeChangeFitnessFunction.getNumberOfCoinsAtGene(

	bestSolutionSoFar, 1 ) + " dimes." );

	System.out.println( "\t" +

	MinimizingMakeChangeFitnessFunction.getNumberOfCoinsAtGene(

	bestSolutionSoFar, 2 ) + " nickels." );

	System.out.println( "\t" +

	MinimizingMakeChangeFitnessFunction.getNumberOfCoinsAtGene(

	bestSolutionSoFar, 3 ) + " pennies." );

	System.out.println( "For a total of " +

	MinimizingMakeChangeFitnessFunction.amountOfChange(

	bestSolutionSoFar ) + " cents in " +

	MinimizingMakeChangeFitnessFunction.getTotalNumberOfCoins(

	bestSolutionSoFar ) + " coins." );

}

/**
 * 
 * Main method. A single command-line argument is expected, which is the
 * 
 * amount of change to create (in other words, 75 would be equal to 75
 * 
 * cents).
 * 
 * 
 * 
 * @param args the command-line arguments.
 *  
 */

public static void main( String[] args )

{

	if ( args.length != 1 )

	{

		System.out.println( "Syntax: MakeChange <amount>" );

	}

	else

	{

		try

		{

			int amount = Integer.parseInt( args[ 0 ] );

			if ( amount < 1 || amount > 99 )

			{

				System.out.println(

				"The <amount> argument must be between 1 and 99." );

			}

			else

			{

				try

				{

					makeChangeForAmount( amount );

				}

				catch ( Exception e )

				{

					e.printStackTrace();

				}

			}

		}

		catch ( NumberFormatException e )

		{

			System.out.println(

			"The <amount> argument must be a valid integer value" );

		}

	}

}

}
