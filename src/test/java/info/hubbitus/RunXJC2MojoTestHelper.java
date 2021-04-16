/*
 * Copyright 2021 Francesco Illuminati <fillumina@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.hubbitus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.project.MavenProject;
import org.jvnet.jaxb2.maven2.AbstractXJC2Mojo;
import org.jvnet.jaxb2.maven2.test.RunXJC2Mojo;

/**
 *
 * @author Francesco Illuminati <fillumina@gmail.com>
 */
public abstract class RunXJC2MojoTestHelper extends RunXJC2Mojo {

    public abstract String getFolderName();

    public String getNamespace() {
        return "";
    }

    // artifact creation happens before test executions!
    public void setUp() throws Exception {
        super.testExecute();
    }

    public void testExecute() throws Exception {
        // override RunXJC2Mojo own method to allow tests to be executed after mojo creation
    }

    @Override
    public File getGeneratedDirectory() {
        return new File(getBaseDir(), "target/generated-sources/" + getFolderName());
    }

    @Override
    public File getSchemaDirectory() {
        return new File(getBaseDir(), "src/test/resources/" + getFolderName());
    }

    @Override
    protected void configureMojo(AbstractXJC2Mojo mojo) {
        super.configureMojo(mojo);
        mojo.setProject(new MavenProject());
        mojo.setForceRegenerate(true);
        mojo.setExtension(true);
    }

    @Override
    public List<String> getArgs() {
        return Collections.singletonList("-" + XJCPluginDescriptionAnnotation.NAME);
    }

    public ArtifactTester element(String elementName) {
        final String filename = elementName + ".java";
        List<String> lines;
        try {
            lines = readFile(filename);
        } catch (IOException ex) {
            throw new AssertionError("error loading file " + filename, ex);
        }
        return new ArtifactTester(filename, lines);
    }

    private List<String> readFile(String filename) throws IOException {
        String ns = getNamespace();
        ns = ns.trim().isEmpty() ? "generated" : ns;
        String fullPath = getGeneratedDirectory().getAbsolutePath() + File.separator +
                ns + File.separator + filename;
        Path path = Paths.get(fullPath);
        List<String> content = Files.readAllLines(path);
        return content;
    }

    public class ArtifactTester {

        private final String filename;
        private final List<String> lines;

        private ArtifactTester(String filename, List<String> lines) {
            this.filename = filename;
            this.lines = lines;
        }

        public AttributeTester classAnnotations() {
            final String clazzName = filename.replace(".java", "");
            int line = getLineForClass(clazzName);
            List<String> annotationList = getAnnotations(clazzName, line);
            String definition = lines.get(line);
            return new AttributeTester(this, filename, clazzName,
                    definition, annotationList);
        }

        public AttributeTester attribute(String attributeName) {
            int line = getLineForAttribute(attributeName);
            List<String> annotationList = getAnnotations(attributeName, line);
            String definition = lines.get(line);
            return new AttributeTester(this, filename, attributeName,
                    definition, annotationList);
        }

        public List<String> getAnnotations(String attributeName) {
            int line = getLineForAttribute(attributeName);
            return getAnnotations(attributeName, line);
        }

        private List<String> getAnnotations(String attributeName, int line) {
            int prevAttribute = prevAttributeLine(attributeName, line);
            List<String> annotationList = lines.subList(prevAttribute, line);
            return annotationList;
        }

        public RunXJC2MojoTestHelper end() {
            return RunXJC2MojoTestHelper.this;
        }

        private int getLineForClass(String className) {
            for (int i = 0, l = lines.size(); i < l; i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("public class " + className)) {
                    return i;
                }
            }
            throw new AssertionError(
                    "attribute " + className + " not found in file " + filename);
        }

        private int getLineForAttribute(String attributeName) {
            for (int i = 0, l = lines.size(); i < l; i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("protected ") && line.endsWith(attributeName + ";")) {
                    return i;
                }
            }
            throw new AssertionError(
                    "attribute " + attributeName + " not found in file " + filename);
        }

        private int prevAttributeLine(String attributeName, int attributeLine) {
            for (int i = attributeLine - 1; i >= 0; i--) {
                String line = lines.get(i).trim();
                if (line.trim().isEmpty() ||
                        line.startsWith("public ") ||
                        line.startsWith("protected ")) {
                    return i + 1;
                }
            }
            throw new AssertionError(
                    "cannot extract validatitions for " + attributeName + " in file " + filename);
        }
    }

    public static class AttributeTester {

        private final ArtifactTester parent;
        private final String filename;
        private final String attributeName;
        private final String definition;
        private final List<String> annotationList;

        public AttributeTester(ArtifactTester parent, String filename, String attributeName,
                String definition, List<String> annotationList) {
            this.parent = parent;
            this.filename = filename;
            this.attributeName = attributeName;
            this.definition = definition;
            this.annotationList = annotationList;
        }

        public ArtifactTester end() {
            return parent;
        }

        public AttributeTester assertClass(Class<?> clazz) {
            String className = clazz.getSimpleName();
            if (!definition.contains(className + " ")) {
                throw new AssertionError("attribute " + attributeName +
                            " in " + filename + " expected of class " + clazz.getName() +
                        " but is: " + definition);
            }
            return this;
        }

        public AttributeTester assertAnnotationNotPresent(String annotation) {
            long counter = annotationList.stream()
                    .filter(l -> l.trim().startsWith("@" + annotation))
                    .count();
            if (counter != 0) {
                throw new AssertionError("annotation " + annotation + " of attribute " +
                    attributeName + " in " + filename + " found");
            }
            return this;
        }

        public AnnotationTester annotation(String annotation) {
            String line = annotationList.stream()
                    .filter(l -> l.trim().startsWith("@" + annotation))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "annotation " + annotation + " of attribute " + attributeName +
                            " in " + filename + " not found "));
            return new AnnotationTester(this, line, annotation);
        }

        public AttributeTester assertNoAnnotationsPresent() {
            if (!annotationList.isEmpty()) {
                throw new AssertionError(
                        "attribute " + attributeName +
                        " in " + filename + " contains annotations: " + annotationList.toString());
            }
            return this;
        }
    }

    public static class AnnotationTester {

        private final AttributeTester parent;
        private final String line;
        private final String annotation;
        private final Map<String, String> valueMap = new HashMap<>();

        public AnnotationTester(AttributeTester parent, String line, String annotationName) {
            this.parent = parent;
            this.line = line;
            this.annotation = annotationName;

            parseAnnotationValues(line);
        }

        private void parseAnnotationValues(String line1) {
            String values = "";
            if (line1.contains("(")) {
                int start = line1.indexOf("(");
                values = line1.substring(start + 1, line1.length() - 1);
            }
            if (!values.trim().isEmpty()) {
                if (line1.contains("=")) {
                    String[] pairs = values.split(",");
                    for (String p : pairs) {
                        String[] kv = p.split("=");
                        valueMap.put(kv[0].trim(), kv[1].trim());
                    }
                } else {
                    valueMap.put("value", values);
                }
            }
        }

        public AttributeTester end() {
            return parent;
        }

        public AttributeTester assertNoValues() {
            if (!valueMap.isEmpty()) {
                throw new AssertionError("annotation " + annotation +
                        " of attribute " + parent.attributeName +
                        " in " + parent.filename + " not empty: " + valueMap);
            }
            return parent;
        }

        public AttributeTester assertValue(Object value) {
            assertParam("value", value);
            return parent;
        }

        public AnnotationTester assertParam(String name, Object value) {
            Objects.requireNonNull(value, "parameter " + name + " value cannot be null");
            String v = valueMap.get(name);
            if (v == null) {
                throw new AssertionError("annotation " + annotation +
                        " of attribute " + parent.attributeName +
                        " in " + parent.filename + " not found: " + valueMap);
            }
            while (v.startsWith("\"")) {
                v = v.substring(1, v.length()-1);
            }
            if (!v.equals(value.toString())) {
                throw new AssertionError("annotation " + annotation +
                        " of attribute " + parent.attributeName +
                        " in " + parent.filename + " mismatched value, expected: " + value +
                        " found " + v);
            }
            return this;
        }

    }

}
