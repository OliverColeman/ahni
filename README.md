# AHNI - Another HyperNEAT Implementation

AHNI implements the HyperNEAT neuroevolution algorithm, see http://eplex.cs.ucf.edu/hyperNEATpage/HyperNEAT.html.

The latest version is available at, and issues should be posted at, https://github.com/OliverColeman/ahni.


## Building and Running

A runnable JAR file can be built from the source files with:

```sh
ant runjar
```

Then to run an experiment:

```sh
java -jar ahni.jar <properties file containing parameters for experiment>
```

For example:

```sh
java -jar ahni.jar properties/or3.properties
```

See `properties/bain-test-pass-through-flip.properties` for an example properties file describing the function of each parameter and setting.

By default a brief summary of the current progress is sent to the log every generation (which for most `example.properties` files goes to the console). 
The summary is a tab-separated list of statistics with a heading line printed every 20 lines. The following values are included:

**Label** | **Description**
--- | ---
Gen | Current generation number.

Fittest | The ID of fittest chromosome.
OvFtns | (Overall) fitness of the fittest chromosome.
Perfor | Performance of the fittest chromosome.

BestPrf | The ID of the chromosome with highest performance.
OvFtns | (Overall) fitness of the chromosome with highest performance.
Perfor | Performance of the chromosome with highest performance.

ZPC | Zero Performance Count, number of chromosomes with a performance of 0.
ZFC | Zero Fitness Count, number of chromosomes with a fitness of 0.
SC | The number of species.
NS | Number of New Species this generation.
ES | Number of Extinct Species this generation.
SCT | Species Compatibility Threshold.
SS | The minimum/maximum species sizes.  
SA | The minimum/maximum species ages (in number of generations).  
SNBP | The number of Species with a New Best Performing chromosome. 
GS | The minimum/average/maximum (CPPN) genome size (total number of nodes and connections). 
Time | The duration of the generation in seconds.
ETA | The estimated run finish time (Days HH:MM:SS).  
Mem | Total memory usage.

By setting the `num.runs` property to a value > 1 it is possible to perform multiple evolutionary runs and have the average fitness / performance results for each generation averaged over all runs. If only one run is performed then most output files will be placed directly in the directory specified by the output.dir property. If multiple runs are performed then the output files specific to a run will be placed in a sub-directory of this directory labelled with the run number.

##Multi-Threading and Computing Clusters

The base fitness function class, `com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT`, allows evaluating the 
population members in parallel. Multiple CPU cores may be utilised on a single machine, and multiple machines may be 
utilised in a cluster.

In the cluster set-up multiple machines are controlled by an initial instance of AHNI. The controller and minions 
communicate via sockets, with each minion instance acting as a server which waits for requests from the controlling 
instance. See `properties/bain-test-pass-through-flip.properties` and the javadoc for 
`com.ojcoleman.ahni.evaluation.BulkFitnessFunctionMT` for more information.

## Development and Creating New Experiments

To create your own experiments you will most likely want to extend `com.ojcoleman.ahni.evaluation.HyperNEATFitnessFunction` or `com.ojcoleman.ahni.evaluation.HyperNEATTargetFitnessFunction`.

For examples see `com.ojcoleman.ahni.experiments.TestTargetFitnessFunction` and `com.ojcoleman.ahni.experiments.objectrecognition`.

The main class is `com.ojcoleman.ahni.hyperneat.Run`. It expects a `.properties` file containing parameters for NEAT, HyperNEAT, typically the specific experiment being run, and various settings.

API documentation is available at http://olivercoleman.github.com/ahni/doc/index.html.

## HyperNEAT-LEO

AHNI supports the Link Expression Output (LEO) extension described in *P. Verbancsics and K. O. Stanley (2011): Constraining Connectivity to Encourage Modularity in HyperNEAT*. In *Proceedings of the Genetic and Evolutionary Computation Conference (GECCO 2011)*.

`properties/retina-problem-hyperneat.properties` reproduces the HyperNEAT-LEO with global locality seed experiment described in the above mentioned paper.

## ES-HyperNEAT

NOTE: A lot of time was spent checking every aspect of the ES-HyperNEAT implementation however it was not possible to reproduce the experiments
in Risi S. and Stanley K.O. (2012) *An Enhanced Hypercube-Based Encoding for Evolving the Placement, Density and Connectivity of Neurons*, Artificial Life, MIT Press,
thus the implementation should be assumed to be be not working. Feel free to try it out and let me know how you go! :)

AHNI supports the Evolvable Substrate HyperNEAT (ES-HyperNEAT) extension (See http://eplex.cs.ucf.edu/ESHyperNEAT/).

Currently only transcription to a Bain NeuralNetwork is supported, via the `com.ojcoleman.ahni.hyperneat.ESHyperNEATTranscriberBain` class. Currently 2D substrates and pseudo-3D substrates are supported. See the second properties file mentioned below for a description of pseudo-3D. Real 3D substrates will likely be coming soon (or let me know if you want to implement this :wink:).

See `ESHN-bain-test-pass-through.properties` and `bain-test-parity.properties` for usage examples (make sure `ann.transcriber.class` is set to `com.ojcoleman.ahni.hyperneat.ESHyperNEATTranscriberBain`).

## NSGA-II multi-objective optimisation

AHNI includes an implementation of the NSGA-II multi-objective optimisation algorithm. See documentation for `com.ojcoleman.ahni.misc.NSGAIISelector` for a complete description, and properties/rl-csb-single.proprties for usage 
examples.

## Novelty Search

AHNI provides support for novelty search via the classes in `com.ojcoleman.ahni.evaluation.novelty.NoveltySearch`.  Some fitness functions have domain-specific support for novelty search (using the above classes). There is also a generic novelty search fitness function that determines novelty by applying the same randomly generated input sequences and comparing the output between individuals.  See `properties/rl-csb-single.proprties` for parameter descriptions.

## Notes

AHNI was built on top of a modified version of ANJI (Another NEAT Java Implementation) by Derek James and Philip Tucker. 
As well as adding code to implement the Hypercube encoding scheme the following changes were made to ANJI:

* Modified to allow using multiple types of activation functions (for HyperNEAT implementation).
* Added more parameters to control speciation, including: minimum species size to select elites1 from; minimum number of elites to select; and target number of species (this is controlled by adjusting the compatibility threshold between species), and a K-Means speciation strategy.
* In the original NEAT algorithm there is a parameter to specify the percentage of individuals used as parents to produce the next generation but that do not necessarily become part of the next generation. In the ANJI implementation this was confused with elitism such that it determined the number of individuals that would survive intact to the next generation.
* In the original NEAT algorithm there is a parameter to specify the maximum number of generations a species can survive without improvement in its fitness value (after which all the individuals in it will be removed and not selected for reproduction). This was added to ANJI, but in experiments was found to hinder performance (for a wide variety of values).
* There was a bug where an individual could be removed from the population list but not from the relevant species member list when the population size was being adjusted after reproduction due to rounding errors. This caused problems when determining the size and average or total fitness of a species.
* There was a bug where elites could be removed from the population when the population size was being adjusted. 
* Numerous other minor API changes and refactoring.

ANJI makes use of a customised version of the JGAP library. Unfortunately this precludes an easy upgrade to more recent versions of JGAP, just in case you were thinking about it.

AHNI was originally written for my Honours project:

Coleman, O.J.: Evolving neural networks for visual processing, 
BS Thesis. University of New South Wales (2010).

It is now being extended for my PhD: "Evolving plastic neural 
networks for online learning". For more details see http://ojcoleman.com .

## License
  
AHNI is licensed under the GNU General Public License v3. A copy of the license is included in the distribution. Please note that AHNI is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. Please refer to the license for details.


AHNI makes use of GifSequenceWriter by Elliot Kroo: http://elliot.kroo.net/software/java/GifSequenceWriter/
GifSequenceWriter is licensed under the Creative Commons Attribution 3.0 Unported License. 
To view a copy of this license, visit http://creativecommons.org/licenses/by/3.0/ or send a letter to 
Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.