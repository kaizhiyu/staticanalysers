# Static Analysers

This is a small collection of static analysers for Java projects, together with a maven plugin to run them. It relies on the [JavaParser](https://github.com/javaparser/javaparser) project for the hard work of parsing java files and symbol solving. 

Be warned, this is "works good enough for me" software. It does not fulfill my criteria for production ready software, mainly because it's only tested by example (no unit test or other systematic tests).   
It may or may not work for you.  

Note: I don't aim for 100% precise analyses. I want to find violations of coding conventions, bugs and bad habits. 
       
## How to run  

Install `staticanalysers-core` and `staticanalysers-maven-plugin` into your local maven repo, then run 
`mvn com.github.kgeilmann:staticanalysers-maven-plugin:1.0.0-SNAPSHOT:analyse` or `mvn com.github.kgeilmann:staticanalysers-maven-plugin:1.0.0-SNAPSHOT:analyse-aggregate` to analyse your project.

## Analyses
  
- [x] Usage of wrong Logger (log4j 1.2 only): If org.apache.log4j.Logger#getLogger(Class) is called with X.class, X should be the surrounding type of the call.
- [X] Usage of not overwritten `Object.toString()` in logger calls. This usually means, we get a not so helpful log message containing stuff like x.y.z.SomeJavaClass@123566
  - [ ] Improvement: usage on abstract types is acceptable if all existing subtypes have an overwritten toString-method. Assumes closed world.
  - [ ] Improvement: better handling of usage on types with wildcards (especially those without bounds)   

Some more ideas, that I currently have no plans to implement them in the near future. 
  
- Usage of not overwritten `Object.equals()` or `Object.hashCode()`.       
- Log entries not starting with an uppercase letter
- `toString` using `org.apache.commons.lang3.builder.ToStringBuilder` with a different style than `SHORT_PREFIX_STYLE`
- `toString` creating long representations probably not suitable for logging in info level 
