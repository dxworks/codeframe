using CSharpFunctionalExtensions;
using CodeAnalysis.Metrics;
using Analysis.Plugins.Models;

namespace CodeAnalysis.DetectionStrategies;

public class DataContainer : IDetectTypeCodeIssue
{
    private const int Few = 4;

    public Maybe<CodeIssue> Detect(ClassModel t)
    {
        var metrics = Metrics.Metrics.For(t);

        var woc = metrics.WeightOfAClass;
        var wmc = metrics.WeightedMethodCount;
        IList<FieldModel> publicAttributes = t.PublicAttributes().ToList();
        var nopa = publicAttributes.Count;
        IList<PropertyModel> accessors = t.Accessors().ToList();
        var noam = accessors.Count;

        if (woc < CommonFractionThreshold.OneThird
            && ((nopa + noam > Few && wmc < 31)
                || (nopa + noam > 8 && wmc < 47)))
            return Maybe<CodeIssue>.From(
                new CodeIssue
                {
                    Name = "Data Container",
                    Severity = CalculateSeverity(publicAttributes, accessors, t),
                    SourceFile = t.FilePath,
                    Metrics = new Dictionary<string, double>
                    {
                        { "woc", woc },
                        { "wmc", wmc },
                        { "nopa", nopa },
                        { "noam", noam }
                    }
                });

        return Maybe<CodeIssue>.None;
    }

    public async Task<SomeClass> CalculateBigNumber(int bigNumber, CancellationToken cancellationToken)
    {
        var period = await service.GetLongValue(bigNumber, cancellationToken);

        return new SomeClass
        {
            SomeValue = _dateTimeProvider.UtcNow.AddSeconds(period),
        };
    }

    private static double CalculateSeverity(IList<FieldModel> publicAttributes, IList<PropertyModel> accessors,
        ClassModel type)
    {
        var severityExploit = SeverityExploit(publicAttributes, accessors, type);

        var severityExposure = SeverityExposure(publicAttributes, accessors);

        return (2 * severityExploit + severityExposure) / 3;
    }

    private static double SeverityExposure(IList<FieldModel> publicAttributes, IList<PropertyModel> accessors)
    {
        return LinearNormalization.WithMeasurementRange(Few, 20).ValueFor(publicAttributes.Count + accessors.Count);
    }

    private static double SeverityExploit(IList<FieldModel> publicAttributes, IList<PropertyModel> accessors,
        ClassModel type)
    {
        var attributeAccesses = publicAttributes.SelectMany(pa => pa.Accesses);
        var fieldAccesses = attributeAccesses.Concat(accessors.SelectMany(a => a.Accesses)).ToHashSet();
        var methodsUsingData = fieldAccesses.Select(fa => fa.Caller).ToHashSet();

        var classesUsingPublicData = methodsUsingData.Select(m => m.Entity).ToHashSet();
        classesUsingPublicData.Remove(type);

        return LinearNormalization.WithMeasurementRange(2, 10).ValueFor(classesUsingPublicData.Count);
    }
}

public class ScatteredDependencies : IDetectMethodCodeIssue
{
    public Maybe<CodeIssue> DetectScattered(MethodModel m)
    {
        const int shortMemoryCap = 7;

        //TODO needs nesting
        //var maxNesting = m.ILNestingDepth;
        var metrics = Metrics.Metrics.For(m);
        var cint = metrics.CouplingIntensity;
        var cdisp = metrics.CouplingDispersion;

        if (cint > shortMemoryCap && cdisp >= CommonFractionThreshold.Half)
            return Maybe<CodeIssue>.From(
                new CodeIssue
                {
                    Name = "Scattered Dependencies",
                    Severity = CalculateSeverity(m),
                    SourceFile = m.Entity.FilePath,
                    Metrics = new Dictionary<string, double>
                    {
                        { "cint", cint },
                        { "cdisp", cdisp }
                    }
                });

        return Maybe<CodeIssue>.None;
    }

    private static double CalculateSeverityScattered(MethodModel method)
    {
        List<Tuple<EntityModel, IList<MemberModel>>> relevantCouplingIntensityPerProvider =
            method.CouplingIntensityPerProvider().Where(t => t.Item2.Count >= 7).ToList();

        var relevantOcio = relevantCouplingIntensityPerProvider.Sum(g => g.Item2.Count);
        var severityRelevantOcio = LinearNormalization.WithMeasurementRange(7, 21).ValueFor(relevantOcio);

        var relevantOcdo = relevantCouplingIntensityPerProvider.Count;
        var severityRelevantOcdo = LinearNormalization.WithMeasurementRange(1, 4).ValueFor(relevantOcdo);

        return (2 * severityRelevantOcio + severityRelevantOcdo) / 3;
    }
}

