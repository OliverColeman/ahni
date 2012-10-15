AHNI - Another HyperNEAT Implementation

AHNI implements the HyperNEAT neuroevolution algorithm, see 
http://eplex.cs.ucf.edu/hyperNEATpage/HyperNEAT.html

The latest version is available at, and issues should be posted at,
https://github.com/OliverColeman/ahni


BUILDING AND RUNNING

A runnable JAR file can be built from the source files with:
ant build-jar

Then to run an experiment:
java -jar ahni.jar <properties file containing parameters for experiment>

For example:
java -jar ahni.jar or3.properties

See properties/test-pass-through-flip.properties for an example properties 
file describing the function of each parameter. 


DEVELOPMENT AND CREATING NEW EXPERIMENTS

To create your own experiments you will most likely want to extend 
ojc.ahni.hyperneat.HyperNEATFitnessFunction or 
ojc.ahni.hyperneat.HyperNEATTargetFitnessFunction 
For examples see: 
ojc.ahni.experiments.TestTargetFitnessFunction and 
ojc.ahni.experiments.objectrecognition.* 

The main class is ojc.ahni.hyperneat.Run. It expects a .properties file containing
parameters for NEAT, HyperNEAT, typically the specific experiment being run, 
and a few other things. See properties/* for examples.


NOTES

AHNI was built on top of a modified version of ANJI (Another NEAT Java 
Implementation) by Derek James and Philip Tucker. 
As well as adding code to implement the Hypercube encoding scheme the 
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

No, the package names don't follow the Java conventions of reversed URLs, it
didn't make much sense for someone like me as there aren't any domains that I
want to attach the project to.

AHNI was originally written for my Honours project:  
Coleman, O.J.: Evolving neural networks for visual processing, 
BS Thesis. University of New South Wales (2010).

It is now being extended for my PhD: "Evolving plastic neural 
networks for online learning". For more details see http://ojcoleman.com .


LICENSE
  
AHNI is licensed under the GNU General Public License v3. A copy of the license
is included in the distribution. Please note that Bain is distributed WITHOUT 
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
FOR A PARTICULAR PURPOSE. Please refer to the license for details.