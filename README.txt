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
file describing the function of each parameter and setting.

By default a line containing a brief summary of the current progress is sent 
to the log (which for most example .properties files goes to the console). The
line looks something like this:

INFO  Gen: 635  Fittest: 320278  (F: 0.0069  P: 0.4375)  Best perf: 321496  
(F: 0.0057  P: 0.4414)  ZFC: 0  ABSF: 0.0053  S: 26  NS/ES: 0/0  SCT: 0.7  
Min/Max SS: 12/29  Min/Max SA: 7/636  SNF: 12  Time: 0s  ETA: 0 00:04:57  
Mem: 119MB

The various labels are:
Gen: Current generation number.
Fittest: The ID of fittest chromosome (it's fitness level, and performance)
Best perf: The ID of the  chromosome with highest performance (it's fitness level, and performance)
ZFC: Zero Fitness Count, number of chromosomes with a fitness of 0.
ABSF: Average Best Species Fitness
S: The number of species.
NS/ES: Number of New Species / Extinct Species this generation.
SCT: Species Compatibility Threshold  .
Min/Max SS: The minimum and maximum species sizes.  
Min/Max SA: The minimum and maximum species ages (in number of generations).  
SNF: The number of Species with a New Fittest chromosome.  
Time: The duration of the generation in seconds.
ETA: The estimated run finish time (Days HH:MM:SS).  
Mem: Total memory usage.

By setting the num.runs property to a value > 1 it is possible to perform 
multiple evolutionary runs and have the average fitness / performance results
for each generation averaged over all runs. If only one run is performed then 
most output files will be placed directly in the directory specified by the
output.dir property. If multiple runs are performed then the output files 
specific to a run will be placed in a sub-directory of this directory labelled 
with the run number.


DEVELOPMENT AND CREATING NEW EXPERIMENTS

To create your own experiments you will most likely want to extend 
ojc.ahni.hyperneat.HyperNEATFitnessFunction or 
ojc.ahni.hyperneat.HyperNEATTargetFitnessFunction 
For examples see: 
ojc.ahni.experiments.TestTargetFitnessFunction and 
ojc.ahni.experiments.objectrecognition.* 

The main class is ojc.ahni.hyperneat.Run. It expects a .properties file containing
parameters for NEAT, HyperNEAT, typically the specific experiment being run, 
and various settings.

API documentation is available at http://olivercoleman.github.com/ahni/doc/index.html


HyperNEAT-LEO

AHNI supports the Link Expression Output (LEO) extension described in 
P. Verbancsics and K. O. Stanley (2011) Constraining Connectivity to Encourage 
Modularity in HyperNEAT. In Proceedings of the Genetic and Evolutionary 
Computation Conference (GECCO 2011).

properties/retina-problem-hyperneat.properties reproduces the HyperNEAT-LEO 
with global locality seed experiment described in the above mentioned paper.


ES-HyperNEAT

AHNI supports the Evolvable Substrate HyperNEAT (ES-HyperNEAT) extension (See
http://eplex.cs.ucf.edu/ESHyperNEAT/).

Currently only transcription to a Bain NeuralNetwork is supported, via the 
ojc.ahni.hyperneat.ESHyperNEATTranscriberBain class. Currently 2D 
substrates and pseudo-3D substrates are supported. See the second properties 
file mentioned below for a description of pseudo-3D. Real 3D substrates will 
likely be coming soon (or let me know if you want to implement this ;)). 

See ESHN-bain-test-pass-through.properties and bain-test-parity.properties 
for usage examples (make sure ann.transcriber.class is set to 
ojc.ahni.hyperneat.ESHyperNEATTranscriberBain).


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
* Numerous other minor API changes and refactoring.

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