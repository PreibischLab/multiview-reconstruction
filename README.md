[![Build Status](https://github.com/PreibischLab/multiview-reconstruction/actions/workflows/build.yml/badge.svg)](https://github.com/PreibischLab/multiview-reconstruction/actions/workflows/build.yml)

##  Introduction & Overview

Selective Plane Illumination Microscopy
([SPIM, Science, 305(5686):1007-9](http://www.sciencemag.org/content/305/5686/1007))
allows in toto imaging of large specimens by acquiring image stacks from
multiple angles. However, to realize the full potential of these acquisitions
the data needs to be reconstructed. This project implements several algorithms
for the registration and fusion of multi-angle SPIM acquisitions.

## Installation

The easiest is to use the multi-view reconstruction is as part of [Fiji](http://fiji.sc/), it is part of [BigStitcher](https://www.google.com/search?rls=en&q=BigStitcher&ie=UTF-8&oe=UTF-8).

You can also check the **outdated** [Multiview-Reconstruction page on the ImageJ
wiki](http://imagej.net/Multiview-Reconstruction).

For questions, bug reports, remarks and comments just use github here or send
me an email: preibischs@janelia.hhmi.org

If you want to **build the code** you can use Maven calling `mvn clean package` on the command line after checking the project out. Important: you will need to install Java with JavaFX (Java8 should work), for example available here in the [Azul JDK](https://www.azul.com/downloads/?version=java-8-lts&package=jdk-fx#zulu).

## Citation

Please note that the Multiview-Reconstruction/BigStitcher plugin available through Fiji, is
based on publications. If you use it successfully for your research please be
so kind to cite our work:

* D. Hörl, F.R. Rusak, F. Preusser, P. Tillberg, N. Randel, R.K. Chhetri, A. Cardona, P.J. Keller, H. Harz, H. Leonhardt, M. Treier & S. Preibisch, "BigStitcher: reconstructing high-resolution image datasets of cleared and expanded samples",
  Nature Methods, 16: 870–874.
  [Webpage](https://www.nature.com/articles/s41592-019-0501-0)
  
* S. Preibisch, S. Saalfeld, J. Schindelin and P. Tomancak (2010) "Software for
  bead-based registration of selective plane illumination microscopy data",
  Nature Methods, 7(6):418-419.
  [Webpage](http://www.nature.com/nmeth/journal/v7/n6/full/nmeth0610-418.html)

* S. Preibisch, F. Amat, E. Stamataki, M. Sarov, R.H. Singer, E. Myers and P.
  Tomancak (2014) “Efficient Bayesian-based Multiview Deconvolution”, Nature
  Methods, 11(6):645-648.
  [Webpage](http://www.nature.com/nmeth/journal/v11/n6/full/nmeth.2929.html)
