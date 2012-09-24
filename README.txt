AHNI - Another HyperNEAT Implementation

Copyright Oliver J. Coleman, 2012. oliver.coleman@gmail.com

AHNI implements the HyperNEAT neuroevolution algorithm:
Stanley, K.O., D’Ambrosio, D.B., Gauci, J.: A Hypercube-Based Indirect 
Encoding for Evolving Large-Scale Neural Networks. Artificial Life. 15, 
185–212 (2009).

AHNI was originally written for my Honours project:  
Coleman, O.J.: Evolving neural networks for visual processing, 
BS Thesis. University of New South Wales (2010).
The Object Recognition experiments in the package 
com.anji.experiments.objectrecognition correspond to those described in this
report (the robot navigation experiments have been removed, contact me to get
the code for these).
 
It is now being extended for my PhD: "Evolving plastic neural 
networks for online learning". For more details see http://ojcoleman.com.

The latest version is available at, and issues should be posted at,
https://github.com/OliverColeman/ahni

Decent getting started information coming eventually... 
The main class is com.anji.neat.Run. It expects a .properties file containing
parameters for NEAT, HyperNEAT, and typically the specific experiment being
run. See properties/* for examples. 
See com.anji.experiments.objectrecognition.* for examples of fitness 
functions.

AHNI was built on top of a modified version of ANJI (Another NEAT Java 
Implementation) by Derek James and Philip Tucker. 
As well as adding code to implement the HyperCUBE encoding scheme the 
following changes were made to ANJI:
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
  
AHNI is licensed under the GNU General Public License v3. A copy of the license
is included in the distribution. Please note that Bain is distributed WITHOUT 
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
FOR A PARTICULAR PURPOSE. Please refer to the license for details.