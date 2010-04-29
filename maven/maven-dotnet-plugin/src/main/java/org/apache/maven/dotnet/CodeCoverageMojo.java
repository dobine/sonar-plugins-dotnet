/**
 * Maven and Sonar plugin for .Net
 * Copyright (C) 2010 Jose Chillan and Alexandre Victoor
 * mailto: jose.chillan@codehaus.org or alexvictoor@codehaus.org
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

/*
 * Created on Apr 16, 2009
 */
package org.apache.maven.dotnet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.dotnet.commons.project.VisualStudioProject;
import org.apache.maven.dotnet.commons.project.VisualStudioSolution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Generates a coverage report on a .Net project or solution using the unit tests and PartCover
 * @goal coverage
 * @phase site
 * @description generates a report on code coverage of a .Net project or solution
 * @author Jose CHILLAN Apr 16, 2009
 */
public class CodeCoverageMojo
  extends GallioMojo
{
  /**
   * Name of the resource folder that contains the Partcover exe
   */
  public static final String PART_RESOURCE_DIR = "partcover";

  /**
   * Name of the folder that will contain the extracted PartCover
   */
  public static final String PART_EXPORT_PATH  = "partcover-runtime";

  /**
   * Location of the PartCover installation.
   * 
   * @parameter expression="${partcover.directory}"
   */
  protected File             partCoverDirectory;

  /**
   * Name of the PartCover executable.
   * 
   * @parameter expression="${partcover.executable}" default-value="PartCover.exe"
   */
  protected String           partCoverExecutable;

  private File               executable;
  /**
   * Namespaces and assemblies excluded from the code coverage. The format is the PartCover format: "[assembly]namespace"
   * 
   * @parameter alias="coverageExcludes"
   */
  protected String[]         coverageExcludes;

  /**
   * Name of the coverage report XML file.
   * 
   * @parameter alias="coverageReportName" default-value="coverage-report.xml";
   */
  protected String           coverageReportName;

  /**
   * The output file for test.
   * 
   * @parameter alias="coverageReportFileName" default-value="coverage-test-report.xml"
   */
  protected String           coverageReportFileName;

  public CodeCoverageMojo()
  {
  }

  /**
   * 
   * Executes for a project (not supported for coverage)
   * 
   * @param visualProject
   * @throws MojoExecutionException
   * @throws MojoFailureException
   */
  @Override
  protected void executeProject(VisualStudioProject visualProject) throws MojoExecutionException, MojoFailureException
  {
    getLog().info("Cannot launch code coverage on a single project");
  }

  @Override
  protected void executeSolution(VisualStudioSolution solution) throws MojoExecutionException, MojoFailureException
  {
    // For code coverage, we don't care of failures
    this.testFailureIgnore = true;
    super.executeSolution(solution);
    getLog().info("Finished the code coverage");
  }

  /**
   * @return
   */
  @Override
  protected String getCommandName()
  {
    return "Code coverage";
  }

  /**
   * Generates a part cover specific test command.
   * 
   * @param solution
   * @param testedAssemblies
   * @return the command to run
   * @throws MojoExecutionException
   * @throws MojoFailureException
   */
  @Override
  protected Commandline generateTestCommand(VisualStudioSolution solution, List<File> testedAssemblies)
                                                                                                       throws MojoExecutionException,
                                                                                                       MojoFailureException
  {
    List<VisualStudioProject> projects = solution.getProjects();
    List<File> testAssemblies = new ArrayList<File>();
    List<String> coveredAssemblies = new ArrayList<String>();
    // We build the list to of covered assemblies
    for (VisualStudioProject visualProject : projects)
    {
      if (!visualProject.isTest())
      {
        String assemblyName = visualProject.getAssemblyName();
        coveredAssemblies.add(assemblyName);
      }
    }

    List<String> testCommandArgs = generateGallioArgs(testedAssemblies);
    // Extracts PartCover if necessary
    extractPartCover();

    // Builds the launch data
    File workDir = getReportDirectory();
    File reportFile = getReportFile(coverageReportName);
    File gallioExe = getGallioExe();
    Log log = getLog();
    if (log.isDebugEnabled())
    {
      log.debug("Parameters of the PartCover execution");
      log.debug(" - PartCover          : " + executable);
      log.debug(" - Gallio             : " + gallioExe);
      log.debug(" - Test assemblies    : " + testAssemblies);
      log.debug(" - Covered assemblies : " + coveredAssemblies);
      log.debug(" - Exclusions         : " + Arrays.toString(coverageExcludes));
      log.debug(" - Work directory     : " + workDir);
      log.debug(" - Report file        : " + reportFile);
    }

    List<String> arguments = new ArrayList<String>();
    arguments.add("--target");
    arguments.add(toCommandPath(gallioExe));
    arguments.add("--target-work-dir");
    arguments.add(toCommandPath(workDir));
    // We add all the tested assemblies

    // Generates the test arguments all in one, in which the quotes needs to be escaped
    StringBuilder targetArgsBuilder = new StringBuilder();
    targetArgsBuilder.append('"');
    boolean isFirst = true;
    for (String currentArg : testCommandArgs)
    {
      if (isFirst)
      {
        isFirst = false;
      }
      else
      {
        targetArgsBuilder.append(' ');
      }
      String escapedArg = escapeQuotes(currentArg);
      targetArgsBuilder.append(escapedArg);
    }
    targetArgsBuilder.append('"');
    arguments.add("--target-args");
    arguments.add(targetArgsBuilder.toString());

    // We add all the covered assemblies
    for (String assemblyName : coveredAssemblies)
    {
      arguments.add("--include");
      arguments.add("[" + assemblyName + "]*");
    }

    // We add all the configured exclusions
    if (coverageExcludes != null)
    {
      for (String exclusion : coverageExcludes)
      {
        arguments.add("--exclude");
        arguments.add(exclusion);
      }
    }
    arguments.add("--output");
    arguments.add(toCommandPath(reportFile));
    Commandline commandLine = generateCommandLine(executable, arguments);
    return commandLine;
  }

  /**
   * Escapes the quotes of a string.
   * 
   * @param input
   * @return
   */
  public String escapeQuotes(String input)
  {
    StringBuilder result = new StringBuilder();
    for (int idxChar = 0; idxChar < input.length(); idxChar++)
    {
      char currentChar = input.charAt(idxChar);
      if (currentChar == '"')
      {
        result.append('\\');
      }
      result.append(currentChar);
    }
    return result.toString();
  }

  /**
   * Extracts the part cover runtime if necessary.
   * 
   * @throws MojoExecutionException
   * @throws MojoFailureException
   */
  private void extractPartCover() throws MojoExecutionException, MojoFailureException
  {
    if (partCoverDirectory == null)
    {
      partCoverDirectory = extractFolder(PART_RESOURCE_DIR, PART_EXPORT_PATH, "PartCover");
    }
    executable = new File(partCoverDirectory, partCoverExecutable);
  }
}