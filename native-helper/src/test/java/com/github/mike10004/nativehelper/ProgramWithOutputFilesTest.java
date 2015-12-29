/*
 * The MIT License
 *
 * Copyright 2015 mchaberski.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.mike10004.nativehelper;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.novetta.ibg.common.sys.Platform;
import com.novetta.ibg.common.sys.Platforms;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author mchaberski
 */
public class ProgramWithOutputFilesTest {
    
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testExecute_outputToFiles() throws IOException {
        System.out.println("testExecute_outputToFiles");
        
        File stdoutFile = temporaryFolder.newFile();
        File stderrFile = temporaryFolder.newFile();
        
        Program.Builder builder = ProgramBuilders.shell();
        ProgramWithOutputFiles program = builder.arg("echo hello").outputToFiles(stdoutFile, stderrFile);
        ProgramWithOutputFilesResult result = program.execute();
        System.out.println("exit code " + result.getExitCode());
        assertEquals("exitCode", 0, result.getExitCode());
        String actualStdout = Files.toString(stdoutFile, Charset.defaultCharset());
        System.out.println(actualStdout);
        assertEquals("hello" + System.getProperty("line.separator"), actualStdout);
        assertEquals("stderr.length " + stderrFile.length(), 0L, stderrFile.length());
    }

    @Test
    public void testExecute_tempDirSpecified() throws IOException {
        System.out.println("testExecute_tempDirSpecified");
        
        File tempDir = temporaryFolder.newFolder();
        
        Program.Builder builder = ProgramBuilders.shell();
        ProgramWithOutputFiles program = builder.arg("echo hello").outputToTempFiles(tempDir.toPath());
        ProgramWithOutputFilesResult result = program.execute();
        System.out.println("exit code " + result.getExitCode());
        assertEquals("exitCode", 0, result.getExitCode());
        
        // check that they're the only two files in the temp dir
        Set<File> filesInTempDir = ImmutableSet.copyOf(FileUtils.listFiles(tempDir, null, true));
        assertEquals("expect 2 files in temp dir", 2, filesInTempDir.size());
        assertEquals(filesInTempDir, ImmutableSet.of(result.getStdoutFile(), result.getStderrFile()));
        File stdoutFile = result.getStdoutFile();
        String actualStdout = Files.toString(stdoutFile, Charset.defaultCharset());
        System.out.println(actualStdout);
        assertEquals("hello" + System.getProperty("line.separator"), actualStdout);
        File stderrFile = result.getStderrFile();
        assertEquals("stderr.length " + stderrFile.length(), 0L, stderrFile.length());
        
    }
    
}
