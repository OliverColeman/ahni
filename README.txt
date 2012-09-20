Another HyperNEAT Implementation (AHNI)

This software is licensed under the GPL v3. See the LICENSE.txt file in the 
same directory as this file for more information.

To run: java -jar ahni.jar <properties file> [<results output file>]

eg: java -jar ahni.jar properties/or4.properties
or: java -jar ahni.jar properties/rn2.properties

The Robot Navigation (rn*.properties) make use of the Simbad robot simulator,
which makes use of the Java3D API. To use the Java3D API it may be necessary
to add the library file to the execution environment. On Linux this can be 
done with something like:
export LD_LIBRARY_PATH=lib/j3d/amd64
You will need to use the right library for your system.

AHNI was built on top of a modified version of ANJI (Another NEAT Java 
Implementation). As well as adding code to implement the HyperCUBE encoding 
scheme and a layer/grid-type neural network simulator, the following changes 
were made to ANJI:

* Modified to allow using multiple types of activation functions (for 
  HyperNEAT implementation).
* Added more parameters to control speciation, including: minimum species size 
  to select elites1 from; minimum number of elites to select; and target number 
  of species (this is controlled by adjusting the compatibility threshold 
  between species).
* In the original NEAT algorithm there is a parameter to specify the 
  percentage of individuals used as parents to produce the next generation but 
  that do not necessarily become part of the next generation. In the ANJI 
  implementation this was confused with elitism such that it determined the 
  number of individuals that would survive intact to the next generation.
* In the original NEAT algorithm there is a parameter to specify the maximum 
  number of generations a species can survive without improvement in its 
  fitness value (after which all the individuals in it will be removed and not 
  selected for reproduction). This was added to ANJI, but in experiments was 
  found to hinder performance (for a wide variety of values).
* There was a bug where an individual could be removed from the population 
  list but not from the relevant species member list when the population size 
  was being adjusted after reproduction due to rounding errors. This caused 
  problems when determining the size and average or total fitness of a species.
* There was a bug where elites could be removed from the population when the 
  population size was being adjusted. 


  ANJI makes use of a customised version of the JGAP library. Unfortunately
  this precludes an easy upgrade to more recent versions of JGAP, just in case
  you were thinking about it.