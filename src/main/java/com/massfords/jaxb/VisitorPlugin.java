package com.massfords.jaxb;

import com.sun.codemodel.*;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CClassInfoParent;
import com.sun.tools.xjc.outline.Aspect;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;

/**
 * Plugin generates the following code:
 * 
 * <ul>
 * <li>Visitor: interface with visit methods for each of the beans</li>
 * <li>Traverser: interface with traverse methods for each of the beans. The traverser traverses each of the bean's children and visits them.
 * <li>BaseVisitor: no-op impl of the Visitor interface</li>
 * <li>DepthFirstTraverserImpl: depth first implementation of the traverser interface</li>
 * <li>TraversingVisitor: class that pairs the visitor and traverser to visit the whole graph</li>
 * <li>accept(Visitor): added to each of the generated JAXB classes</li>
 * </ul>
 * 
 * @author markford
 */
public class VisitorPlugin extends Plugin {
    
    /**
     * name of the package for our generated visitor classes. If not set, we'll pick the first package from the outline.
     */
    private String packageName;

    @Override
    public String getOptionName() {
        return "Xvisitor";
    }

    @Override
    public String getUsage() {
        return null;
    }
    
    @Override
    public int parseArgument(Options opt, String[] args, int index) throws BadCommandLineException, IOException {
    	
    	// look for the visitor-package argument since we'll use this for package name for our generated code.
        String arg = args[index];
        if (arg.startsWith("-Xvisitor-package:")) {
            packageName = arg.split(":")[1];
            return 1;
        }
        return 0;
    }

    @Override
    public boolean run(Outline outline, Options options, ErrorHandler errorHandler)
            throws SAXException {
        try {

            /*
               // create a set to hold all of the beans that need a qname
               // add a qname field to each of these beans
               // add a getter/setter for the qname via an interface
               // add unmarshaller hook to each of these beans to pull the qname from their JAXBElement parent
               // update the traverser code for JAXBElement to see if the bean is an instance of this interface and invoke the setter
               // done and done
            */

            JPackage vizPackage = getOrCreatePackageForVisitors(outline);

            Set<ClassOutline> sorted = sortClasses(outline);

            // create JAXBElement name support for holding JAXBElement names
            CreateJAXBElementNameCallback cni = new CreateJAXBElementNameCallback(outline, vizPackage);
            cni.run(sorted);

            // create visitor interface
            CreateVisitorInterface createVisitorInterface = new CreateVisitorInterface(outline, vizPackage);
            createVisitorInterface.run(sorted);
            JDefinedClass visitor = createVisitorInterface.getOutput();
            
            // create visitable interface and have all the beans implement it
            CreateVisitableInterface createVisitableInterface = new CreateVisitableInterface(visitor, outline, vizPackage);
            createVisitableInterface.run(sorted);
            JDefinedClass visitable = createVisitableInterface.getOutput();
            
            // add accept method to beans
            AddAcceptMethod addAcceptMethod = new AddAcceptMethod();
            addAcceptMethod.run(sorted, visitor);
            
            // create traverser interface
            CreateTraverserInterface createTraverserInterface = new CreateTraverserInterface(visitor, outline, vizPackage);
            createTraverserInterface.run(sorted);
            JDefinedClass traverser = createTraverserInterface.getOutput();
            
            // create abstract base visitor class
            CreateBaseVisitorClass createAbstractBaseVisitorClass = new CreateBaseVisitorClass(visitor, outline, vizPackage);
            createAbstractBaseVisitorClass.run(sorted);
            createAbstractBaseVisitorClass.getOutput();

            // create default generic traverser class
            CreateDepthFirstTraverserClass createDepthFirstTraverserClass = new CreateDepthFirstTraverserClass(visitor, traverser, visitable, outline, vizPackage);
            createDepthFirstTraverserClass.run(sorted);
            
            // create progress monitor for traversing visitor
            CreateTraversingVisitorProgressMonitorInterface createTraversingVisitorProgressMonitorInterface = new CreateTraversingVisitorProgressMonitorInterface(visitable, outline, vizPackage);
            createTraversingVisitorProgressMonitorInterface.run(sorted);
            JDefinedClass progressMonitor = createTraversingVisitorProgressMonitorInterface.getOutput();
            
            // create traversing visitor class
            CreateTraversingVisitorClass createTraversingVisitorClass = new CreateTraversingVisitorClass(visitor, progressMonitor, traverser, outline, vizPackage);
            createTraversingVisitorClass.run(sorted);
            
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }


    /**
     * The classes are sorted for test purposes only. This gives us a predictable order for our 
     * assertions on the generated code.
     * 
     * @param outline
     */
    private Set<ClassOutline> sortClasses(Outline outline) {
        Set<ClassOutline> sorted = new TreeSet<>(new Comparator<ClassOutline>() {
            @Override
            public int compare(ClassOutline aOne, ClassOutline aTwo) {
                String one = aOne.implClass.fullName();
                String two = aTwo.implClass.fullName();
                return one.compareTo(two);
            }
        });
        for (ClassOutline classOutline : outline.getClasses()) {
            // skip over abstract classes
//            if (!classOutline.target.isAbstract()) {
                sorted.add(classOutline);
//            }
        }
        return sorted;
    }

    private JPackage getOrCreatePackageForVisitors(Outline outline) {
        JPackage vizPackage = null;
        if (getPackageName() != null) {
            JPackage root = outline.getCodeModel().rootPackage();
            String[] packages = getPackageName().split("\\.");
            JPackage current = root;
            for(String p : packages) {
                current = current.subPackage(p);
            }
            vizPackage = current;
        }
        if (vizPackage == null) {
            PackageOutline packageOutline = outline.getAllPackageContexts().iterator().next();
            CClassInfoParent.Package pkage = new CClassInfoParent.Package(packageOutline._package());
            vizPackage = (JPackage) outline.getContainer(pkage, Aspect.IMPLEMENTATION);
        }
        return vizPackage;
    }
    
    public String getPackageName() {
        return packageName;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

}
