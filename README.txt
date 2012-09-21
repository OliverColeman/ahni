AHNI - Another HyperNEAT Implementation

This software is licensed under the GPL v3. See the LICENSE.txt file in the 
same directory as this file for more information.


The main class is com.anji.neat.Run. It expects a .properties file containing
parameters for NEAT, HyperNEAT, and typically the specific experiment being
run. See properties/* for examples. 


The latest version is available at, and issues should be posted at,
https://github.com/OliverColeman/ahni


This software is being written as part of my PhD: "Evolving plastic neural 
networks for online learning". For more details see http://ojcoleman.com.


Getting started information coming eventually... 
See com.anji.experiments.objectrecognition.* for examples of fitness 
functions.


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