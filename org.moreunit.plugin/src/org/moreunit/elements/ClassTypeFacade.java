package org.moreunit.elements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ui.IEditorPart;
import org.moreunit.log.LogHandler;
import org.moreunit.preferences.Preferences;
import org.moreunit.ui.ChooseDialog;
import org.moreunit.ui.CreateNewClassAction;
import org.moreunit.ui.MemberContentProvider;
import org.moreunit.util.MethodCallFinder;
import org.moreunit.util.MethodTestCallerFinder;
import org.moreunit.util.TestCaseDiviner;
import org.moreunit.util.TestMethodDiviner;
import org.moreunit.util.TestMethodDivinerFactory;
import org.moreunit.wizards.NewClassyWizard;
import org.moreunit.wizards.NewTestCaseWizard;

/**
 * ClassTypeFacade offers easy access to a simple java file within eclipse. The
 * file represented by this instance is not a testcase.
 * <p>
 * 30.09.2010 Gro Added Method {@link #isNewTestClassCreated()}
 * 
 * @author vera 23.05.2006 20:28:52
 * @version 30.09.2010
 */
public class ClassTypeFacade extends TypeFacade
{

    private TestCaseDiviner testCaseDiviner;
    TestMethodDivinerFactory testMethodDivinerFactory;
    TestMethodDiviner testMethodDiviner;

    public ClassTypeFacade(ICompilationUnit compilationUnit)
    {
        super(compilationUnit);
        testMethodDivinerFactory = new TestMethodDivinerFactory(compilationUnit);
        testMethodDiviner = testMethodDivinerFactory.create();
    }

    public ClassTypeFacade(IEditorPart editorPart)
    {
        super(editorPart);
        testMethodDivinerFactory = new TestMethodDivinerFactory(compilationUnit);
        testMethodDiviner = testMethodDivinerFactory.create();
    }

    public ClassTypeFacade(IFile file)
    {
        super(file);
        testMethodDivinerFactory = new TestMethodDivinerFactory(compilationUnit);
        testMethodDiviner = testMethodDivinerFactory.create();
    }

    /**
     * Returns the corresponding testcase of the javaFileFacade. If there are
     * more than one testcases the user has to make a choice via a dialog. If no
     * test is found <code>null</code> is returned.
     * 
     * @return one of the corresponding testcases
     */
    public CorrespondingTestCase getOneCorrespondingTestCase(boolean createIfNecessary, String promptText)
    {
        Collection<IType> testcases = getCorrespondingTestCases();

        if(testcases.size() == 1)
        {
            return new CorrespondingTestCase(testcases.iterator().next(), false);
        }
        else if(testcases.size() > 1)
        {
            CreateNewTestCaseAction newTestCaseAction = new CreateNewTestCaseAction(getType());
            MemberContentProvider contentProvider = new MemberContentProvider(testcases, null).withAction(newTestCaseAction);

            IType testCase = new ChooseDialog<IType>(promptText, contentProvider).getChoice();
            return new CorrespondingTestCase(testCase, newTestCaseAction.testCaseCreated);
        }
        else if(createIfNecessary)
        {
            IType testcaseToJump = new NewTestCaseWizard(getType()).open();
            return new CorrespondingTestCase(testcaseToJump, testcaseToJump != null);
        }

        return new CorrespondingTestCase(null, false);
    }

    public Collection<IType> getCorrespondingTestCases()
    {
        return getCorrespondingClasses(false);
    }

    public IMethod getCorrespondingTestMethod(IMethod method, IType testCaseType)
    {
        String nameOfCorrespondingTestMethod = testMethodDiviner.getTestMethodNameFromMethodName(method.getElementName());

        if(testCaseType == null)
        {
            return null;
        }

        try
        {
            IMethod[] methodsOfType = testCaseType.getCompilationUnit().findPrimaryType().getMethods();
            for (IMethod testmethod : methodsOfType)
            {
                if(testmethod.getElementName().startsWith(nameOfCorrespondingTestMethod))
                {
                    return testmethod;
                }
            }
        }
        catch (JavaModelException exc)
        {
            LogHandler.getInstance().handleExceptionLog(exc);
        }

        return null;
    }

    public List<IMethod> getCorrespondingTestMethods(IMethod method)
    {
        Collection<IType> allTestCases = getCorrespondingTestCases();
        return getTestMethodsForTestCases(method, allTestCases);
    }

    private List<IMethod> getTestMethodsForTestCases(IMethod method, Collection<IType> testCases)
    {
        List<IMethod> result = new ArrayList<IMethod>();

        for (IType testCaseType : testCases)
        {
            result.addAll(getTestMethodsForTestCase(method, testCaseType));
        }

        return result;
    }

    private List<IMethod> getTestMethodsForTestCase(IMethod method, IType testCaseType)
    {
        List<IMethod> result = new ArrayList<IMethod>();

        if(testCaseType == null)
        {
            return result;
        }

        String nameOfCorrespondingTestMethod = testMethodDiviner.getTestMethodNameFromMethodName(method.getElementName());

        try
        {
            IMethod[] methodsOfType = testCaseType.getCompilationUnit().findPrimaryType().getMethods();
            for (IMethod testmethod : methodsOfType)
            {
                if(testmethod.getElementName().startsWith(nameOfCorrespondingTestMethod))
                {
                    result.add(testmethod);
                }
            }
        }
        catch (JavaModelException exc)
        {
            LogHandler.getInstance().handleExceptionLog(exc);
        }

        return result;
    }

    public boolean hasTestMethod(IMethod method, MethodSearchMode searchMethod)
    {
        final Set<IMethod> correspondingTestMethods = new HashSet<IMethod>();
        if(searchMethod == MethodSearchMode.BY_CALL)
        {
            Collection<IType> correspondingClasses = getCorrespondingTestCases();
            if(! correspondingClasses.isEmpty())
            {
                correspondingTestMethods.addAll(getCallRelationshipFinder(method, correspondingClasses).getMatches(new NullProgressMonitor()));
            }
        }
        else
        {
            correspondingTestMethods.addAll(getCorrespondingTestMethods(method));
        }
        return ! correspondingTestMethods.isEmpty();
    }

    /**
     * Getter uses lazy caching.
     */
    private TestCaseDiviner getTestCaseDiviner()
    {
        if(this.testCaseDiviner == null)
        {
            this.testCaseDiviner = new TestCaseDiviner(this.compilationUnit, Preferences.getInstance());
        }

        return this.testCaseDiviner;
    }

    @Override
    protected Collection<IType> getCorrespondingClasses(boolean alsoIncludeLikelyMatches)
    {
        return getTestCaseDiviner().getMatches(alsoIncludeLikelyMatches);
    }

    @Override
    protected Collection<IMethod> getCorrespondingMethodsInClasses(IMethod method, Collection<IType> classes)
    {
        return getTestMethodsForTestCases(method, classes);
    }

    @Override
    protected MethodCallFinder getCallRelationshipFinder(IMethod method, Collection<IType> searchScope)
    {
        return new MethodTestCallerFinder(method, searchScope);
    }

    @Override
    protected NewClassyWizard newCorrespondingClassWizard(IType fromType)
    {
        return new NewTestCaseWizard(fromType);
    }

    public CorrespondingTestCase getOneCorrespondingTestCase(boolean createIfNecessary)
    {
        return getOneCorrespondingTestCase(createIfNecessary, "Please choose a test case...");
    }

    public Collection< ? extends IMember> getCorrespondingTestMembers(IMethod method, boolean extendedSearch)
    {
        Collection<IMethod> testMethods = new LinkedHashSet<IMethod>();

        Collection<IType> testCases = getCorrespondingTestCases();

        if(method != null && ! testCases.isEmpty())
        {
            testMethods.addAll(getCorrespondingMethodsInClasses(method, testCases));
            if(extendedSearch)
            {
                testMethods.addAll(getCallRelationshipFinder(method, testCases).getMatches(new NullProgressMonitor()));
            }
        }

        return testMethods.isEmpty() ? testCases : testMethods;
    }

    private static class CreateNewTestCaseAction extends CreateNewClassAction
    {
        private final IType type;
        private boolean testCaseCreated;

        public CreateNewTestCaseAction(IType type)
        {
            this.type = type;
        }

        @Override
        public IType execute()
        {
            IType newTestCase = new NewTestCaseWizard(type).open();
            testCaseCreated = newTestCase != null;
            return newTestCase;
        }
    }

    public static final class CorrespondingTestCase
    {
        private final IType testCase;
        private final boolean justCreated;

        public CorrespondingTestCase(IType testCase, boolean justCreated)
        {
            this.testCase = testCase;
            this.justCreated = justCreated;
        }

        public boolean found()
        {
            return testCase != null;
        }

        public IType get()
        {
            return testCase;
        }

        public boolean hasJustBeenCreated()
        {
            return justCreated;
        }
    }
}
