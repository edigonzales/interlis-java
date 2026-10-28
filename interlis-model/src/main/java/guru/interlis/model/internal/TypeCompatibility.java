package guru.interlis.model.internal;

import guru.interlis.model.*;
import java.math.BigDecimal;
import java.util.List;

/** Internal shared assignment rules; callers supply alias-resolved descriptors from their own state. */
public final class TypeCompatibility {
    private TypeCompatibility() { }

    /** Extension of effective definitions, distinct from assignment of values.
     * Collection elements use extension recursively, preserving unit and precision
     * restrictions. Lookups belong to the caller's compilation or snapshot. */
    public static TypeRelation extend(TypeDescriptor derived, TypeDescriptor base, LanguageVersion language,
            java.util.function.Function<TypeId, TypeDescriptor> resolve,
            java.util.function.Function<SymbolId, SymbolId> baseOf,
            java.util.function.Function<SymbolId, TypeDescriptor> domainType,
            java.util.function.Function<SymbolId, UnitSymbol> units) {
        java.util.Objects.requireNonNull(language, "language");
        if (derived == null || base == null || derived instanceof ErrorType || base instanceof ErrorType
                || derived instanceof PendingType || base instanceof PendingType) return disallowed("unresolved-type");
        if (isUndefined(derived) || isUndefined(base)) return disallowed("undefined-is-a-value-not-a-type-extension");
        if (derived.id().equals(base.id())) return allowed("same-type");
        if(derived instanceof EnumerationType a && base instanceof EnumerationType b && a.treeValues()!=b.treeValues())
            return disallowed("enumeration-value-kind-changed");
        if (derived instanceof NumericType a && base instanceof NumericType b) return numericExtension(a,b,language,units);
        if (derived instanceof FormattedType a && base instanceof FormattedType b) {
            var layout=formattedPrefix(a,b,baseOf,domainType);
            return layout && formattedContained(a,b,domainType) ? allowed("formatted-extension") : disallowed("formatted-layout-or-range-widens");
        }
        if (derived instanceof GeometryType a && base instanceof GeometryType b) return geometryExtension(a,b,baseOf,domainType);
        if (derived instanceof CoordinateType a && base instanceof CoordinateType b) {
            if (a.dimensions()<b.dimensions() || a.multiCoordinate()!=b.multiCoordinate()) return disallowed("coordinate-dimensions");
            if (b.rotation().isPresent() && !a.rotation().equals(b.rotation())
                    || b.referenceSystem().isPresent() && !a.referenceSystem().equals(b.referenceSystem()))
                return disallowed("coordinate-reference-changed");
            for(int i=0;i<b.dimensions();i++) {
                var relation=numericExtension(new NumericType(a.id(),a.origin(),a.axes().get(i)),
                        new NumericType(b.id(),b.origin(),b.axes().get(i)),language,units);
                if(relation.decision()!=RelationDecision.ALLOWED)return disallowed("coordinate-axis-"+relation.reason());
            }
            return allowed("coordinate-extension");
        }
        if(derived instanceof CollectionType a && base instanceof CollectionType b) {
            if(a.collectionKind()!=b.collectionKind() && b.collectionKind()!=CollectionKind.BAG)
                return disallowed("collection-kind-differs");
            if(a.cardinality().lower().compareTo(b.cardinality().lower())<0 || upperCompare(a,b)>0)
                return disallowed("collection-cardinality-widens");
            var element=extend(resolve.apply(a.elementType()),resolve.apply(b.elementType()),language,resolve,baseOf,domainType,units);
            return element.decision()==RelationDecision.ALLOWED?allowed("collection-extension"):disallowed("collection-element-"+element.reason());
        }
        if(derived instanceof ReferenceType a && base instanceof ReferenceType b && a.external() && !b.external())
            return disallowed("reference-external-added");
        if(derived instanceof TextType a && base instanceof TextType b && a.textKind()!=b.textKind())
            return disallowed("text-extension-kind-changed");
        if(derived instanceof TextType a && base instanceof TextType b && (b.textKind()==TextKind.NAME || b.textKind()==TextKind.URI))
            return a.textKind()==b.textKind() && a.maxLength().equals(b.maxLength())
                    ? allowed("unchanged-final-text-type") : disallowed("final-text-type");
        return assign(derived,base,resolve,baseOf,domainType);
    }

    /** Geometry extension over effective, alias-resolved definitions. Unlike value
     * assignment, an established tolerance is immutable and SURFACE may become AREA.
     * The base lookup includes domain and structure inheritance in the same graph. */
    public static TypeRelation geometryExtension(GeometryType derived, GeometryType base,
            java.util.function.Function<SymbolId, SymbolId> baseOf,
            java.util.function.Function<SymbolId, TypeDescriptor> domainType) {
        boolean family = derived.geometryKind() == base.geometryKind()
                || base.geometryKind() == GeometryKind.SURFACE && derived.geometryKind() == GeometryKind.AREA
                || base.geometryKind() == GeometryKind.MULTISURFACE && derived.geometryKind() == GeometryKind.MULTIAREA;
        if (!family) return disallowed("geometry-family-changed");
        if (base.directed() && !derived.directed()) return disallowed("geometry-direction-removed");
        if (!base.lineForms().isEmpty() && (derived.lineForms().isEmpty() || !base.lineForms().containsAll(derived.lineForms())))
            return disallowed("geometry-line-forms-widen");
        if (base.vertexDomain().isPresent()) {
            if (derived.vertexDomain().isEmpty()) return disallowed("geometry-vertex-missing");
            var a = domainType.apply(derived.vertexDomain().orElseThrow());
            var b = domainType.apply(base.vertexDomain().orElseThrow());
            if (!(a instanceof CoordinateType ac && b instanceof CoordinateType bc) || !coordinateRefines(ac, bc))
                return disallowed("geometry-vertex-widens");
        }
        if (base.overlap().isPresent() && !sameTolerance(derived.overlap(), base.overlap()))
            return disallowed("geometry-tolerance-changed");
        if (base.withoutOverlaps() && !derived.withoutOverlaps()) return disallowed("geometry-overlaps-allowed");
        // In 2.3 an extension may omit line attributes, or specialize their structure.
        if (base.lineAttributes().isPresent() && derived.lineAttributes().isPresent()
                && !specializes(derived.lineAttributes().orElseThrow(), base.lineAttributes().orElseThrow(), baseOf))
            return disallowed("geometry-line-attributes-widen");
        return allowed("geometry-extension");
    }

    public static boolean sameTolerance(java.util.Optional<GeometryType.Tolerance> a, java.util.Optional<GeometryType.Tolerance> b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        if (a.orElseThrow() instanceof GeometryType.Explicit x && b.orElseThrow() instanceof GeometryType.Explicit y)
            return x.value().exactValue().compareTo(y.value().exactValue()) == 0;
        return a.equals(b);
    }

    private static boolean toleranceContained(java.util.Optional<GeometryType.Tolerance> actual,
            java.util.Optional<GeometryType.Tolerance> expected,
            java.util.function.Function<SymbolId, TypeDescriptor> domainType) {
        if (sameTolerance(actual, expected)) return true;
        BigDecimal a = toleranceSquaredTwice(actual.orElse(null), domainType);
        BigDecimal b = toleranceSquaredTwice(expected.orElse(null), domainType);
        return a != null && b != null && a.compareTo(b) <= 0;
    }

    // Compare nonnegative tolerances exactly, using 2*t². No square root or
    // rounded decimal is needed for the symbolic track width q*sqrt(2)/2.
    private static BigDecimal toleranceSquaredTwice(GeometryType.Tolerance tolerance,
            java.util.function.Function<SymbolId, TypeDescriptor> domainType) {
        if (tolerance instanceof GeometryType.Explicit explicit) {
            BigDecimal value = explicit.value().exactValue();
            return value.signum() < 0 ? null : value.multiply(value).multiply(BigDecimal.valueOf(2));
        }
        if (tolerance instanceof GeometryType.TrackWidth width
                && domainType.apply(width.coordinateDomain()) instanceof CoordinateType c
                && c.axes().getFirst().minimum().isPresent()) {
            var precision = c.axes().getFirst().minimum().orElseThrow().precision();
            BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(precision.exponent() - precision.fractionalDigits());
            return quantum.multiply(quantum);
        }
        return null;
    }

    /**
     * Checks only the unit restriction of a scalar numeric extension. A concrete
     * base unit is fixed; an abstract base unit may be specialized through bound
     * {@link UnitSymbol#base()} and {@link UnitSymbol#derivedFrom()} identities.
     * Omitted inherited units must already be filled by the caller. Unitless
     * bounded bases remain unitless; an unbounded unitless base may gain a unit.
     * No conversion or numeric range/precision comparison is performed here.
     *
     * @param actual non-null effective derived numeric type
     * @param base non-null effective base numeric type
     * @param lookup non-null snapshot-local lookup, returning null for unresolved units
     * @return a null-free relation; unresolved units and cyclic paths without a
     *         matching base are disallowed
     */
    public static TypeRelation numericUnitExtension(NumericType actual, NumericType base,
            java.util.function.Function<SymbolId, UnitSymbol> lookup) {
        java.util.Objects.requireNonNull(actual, "actual");
        java.util.Objects.requireNonNull(base, "base");
        java.util.Objects.requireNonNull(lookup, "lookup");
        var actualId = actual.range().unit();
        var baseId = base.range().unit();
        UnitSymbol actualUnit = actualId.map(lookup).orElse(null);
        UnitSymbol baseUnit = baseId.map(lookup).orElse(null);
        if (actualId.isPresent() && actualUnit == null || baseId.isPresent() && baseUnit == null)
            return disallowed("numeric-unit-unresolved");
        if (baseId.isEmpty()) {
            boolean bounded = base.range().minimum().isPresent() || base.range().maximum().isPresent();
            return actualId.isPresent() && bounded
                    ? disallowed("numeric-unit-added-to-unitless-base") : allowed("numeric-unit-unconstrained");
        }
        if (actualId.isEmpty()) return disallowed("numeric-unit-missing");
        if (actualId.equals(baseId)) return allowed("numeric-unit-unchanged");
        if (!baseUnit.abstractUnit()) return disallowed("numeric-concrete-unit-changed");
        if (baseUnit.header().origin() instanceof Origin.PredefinedOrigin predefined
                && predefined.key().equals("INTERLIS.ANYUNIT"))
            return allowed("numeric-unit-specialization");
        var pending = new java.util.ArrayDeque<SymbolId>();
        var seen = new java.util.HashSet<SymbolId>();
        pending.add(actualId.orElseThrow());
        while (!pending.isEmpty()) {
            SymbolId id = pending.removeFirst();
            if (!seen.add(id)) continue;
            UnitSymbol unit = lookup.apply(id);
            if (unit == null) return disallowed("numeric-unit-unresolved");
            if (id.equals(baseId.orElseThrow())) return allowed("numeric-unit-specialization");
            unit.base().ifPresent(pending::add);
            unit.derivedFrom().ifPresent(pending::add);
        }
        return disallowed("numeric-unit-not-specialization");
    }

    /** Numeric extension preserves precision of a concrete base and restricts
     * its interval. Conversion compatibility never replaces the unit restriction.
     * Assignment of values remains a distinct rule. */
    public static TypeRelation numericExtension(NumericType actual, NumericType base, LanguageVersion language,
            java.util.function.Function<SymbolId, UnitSymbol> units) {
        TypeRelation unit = numericUnitExtension(actual, base, units);
        if (unit.decision() == RelationDecision.DISALLOWED) return unit;
        if (base.range().referenceSystem().isPresent() && !actual.range().referenceSystem().equals(base.range().referenceSystem()))
            return disallowed("numeric-reference-changed");
        if (base.range().direction()!=NumericDirection.NONE && actual.range().direction()!=base.range().direction())
            return disallowed("numeric-direction-changed");
        if (base.range().circular() && !actual.range().circular()) return disallowed("numeric-circular-removed");
        java.util.Objects.requireNonNull(language, "language");
        if (language == LanguageVersion.ILI23) {
            if (!roundedContained(actual.range().minimum(), base.range().minimum(), true)
                    || !roundedContained(actual.range().maximum(), base.range().maximum(), false))
                return disallowed("numeric-rounded-range-widens");
        } else {
            if (!numericContained(actual, base)) return disallowed("numeric-range-widens");
            if (precisionChanged(base.range().minimum(), actual.range().minimum())
                    || precisionChanged(base.range().maximum(), actual.range().maximum()))
                return disallowed("numeric-precision-changed");
        }
        return allowed("numeric-extension");
    }

    private static boolean roundedContained(java.util.Optional<DecimalLiteral> actual,
            java.util.Optional<DecimalLiteral> base, boolean minimum) {
        if (base.isEmpty()) return true;
        if (actual.isEmpty()) return false;
        DecimalLiteral boundary = base.orElseThrow();
        int scale = boundary.precision().fractionalDigits() - boundary.precision().exponent();
        BigDecimal value = actual.orElseThrow().exactValue().setScale(scale, java.math.RoundingMode.HALF_UP);
        return minimum ? value.compareTo(boundary.exactValue()) >= 0 : value.compareTo(boundary.exactValue()) <= 0;
    }

    private static boolean precisionChanged(java.util.Optional<DecimalLiteral> base, java.util.Optional<DecimalLiteral> derived) {
        return base.isPresent() && derived.isPresent()
                && base.orElseThrow().precision().fractionalDigits() != derived.orElseThrow().precision().fractionalDigits();
    }

    /** Static conversion compatibility, using declared conversion links and
     * composed units. Abstract EXTENDS links never imply a conversion ratio.
     * No constant/function is evaluated. Empty means an unresolved/cyclic or
     * invalid dependency, rather than an arbitrary successful comparison. */
    public static java.util.Optional<UnitRelation> unitRelation(SymbolId left, SymbolId right,
            java.util.function.Function<SymbolId, UnitSymbol> lookup) {
        java.util.Objects.requireNonNull(left, "left");
        java.util.Objects.requireNonNull(right, "right");
        java.util.Objects.requireNonNull(lookup, "lookup");
        var roots = new java.util.HashMap<SymbolId, java.util.Map<SymbolId, java.math.BigInteger>>();
        var active = new java.util.HashSet<SymbolId>();
        record Visit(SymbolId id, boolean exit) { }
        var pending = new java.util.ArrayDeque<Visit>();
        pending.push(new Visit(right, false)); pending.push(new Visit(left, false));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            if (roots.containsKey(visit.id())) continue;
            UnitSymbol unit = lookup.apply(visit.id());
            if (unit == null || unit.derivedFrom().isEmpty() && (!unit.conversionFactors().isEmpty() || unit.conversionFunction().isPresent()) || unit.conversionFactors().stream().anyMatch(f -> f.decimal()
                    .filter(d -> d.exactValue().signum() == 0).isPresent())) return java.util.Optional.empty();
            if (!visit.exit()) {
                if (!active.add(visit.id())) return java.util.Optional.empty();
                pending.push(new Visit(visit.id(), true));
                if (unit.derivedFrom().isPresent()) pending.push(new Visit(unit.derivedFrom().orElseThrow(), false));
                else for (var component : unit.components()) pending.push(new Visit(component.unit(), false));
            } else {
                var factors = new java.util.HashMap<SymbolId, java.math.BigInteger>();
                if (unit.derivedFrom().isPresent()) factors.putAll(roots.get(unit.derivedFrom().orElseThrow()));
                else if (unit.components().isEmpty()) factors.put(unit.id(), java.math.BigInteger.ONE);
                else for (var component : unit.components()) {
                    var sign = component.operator() == UnitSymbol.Operator.MULTIPLY
                            ? java.math.BigInteger.ONE : java.math.BigInteger.ONE.negate();
                    roots.get(component.unit()).forEach((key, power) -> factors.merge(key, power.multiply(sign), java.math.BigInteger::add));
                }
                factors.values().removeIf(java.math.BigInteger.ZERO::equals);
                roots.put(unit.id(), java.util.Map.copyOf(factors)); active.remove(unit.id());
            }
        }
        boolean same = roots.get(left).equals(roots.get(right));
        return java.util.Optional.of(new UnitRelation(same ? RelationDecision.ALLOWED : RelationDecision.DISALLOWED,
                left.equals(right) ? "same-unit" : same ? "declared-conversion-compatible" : "no-common-conversion-unit"));
    }

    /** The untyped undefined constant and a zero-length TEXT literal denote
     * the same undefined value. TEXT*0 cannot be a valid declared domain. */
    public static boolean isUndefined(TypeDescriptor type) {
        return type instanceof GenericType generic && generic.name().equals("UNDEFINED")
                || type instanceof TextType text && text.maxLength().isPresent() && text.maxLength().getAsInt()==0;
    }

    /** Whether both operands denote individual objects and can be compared for identity. */
    public static boolean objectIdentityComparable(TypeDescriptor left, TypeDescriptor right) {
        return isSingleObject(left) && isSingleObject(right);
    }

    /** Operator-specific comparison of effective types. Literal binding (enum
     * members and FORMAT strings) precedes this common compiler/query rule. */
    public static TypeRelation compare(TypeDescriptor left, TypeDescriptor right, ComparisonOperator operator,
            java.util.function.Function<TypeId,TypeDescriptor> resolve,
            java.util.function.Function<SymbolId,TypeDescriptor> declaredType) {
        java.util.Objects.requireNonNull(operator,"operator");
        if(left==null || right==null || left instanceof ErrorType || right instanceof ErrorType)
            return disallowed("unresolved-type");
        boolean equality=operator==ComparisonOperator.EQUALITY;
        if(isUndefined(left) || isUndefined(right))
            return equality?allowed("definedness-comparison"):disallowed("undefined-not-ordered");
        if(objectIdentityComparable(left,right))return equality?allowed("object-identity-comparable"):disallowed("object-not-ordered");
        if(left instanceof ClassValueType && right instanceof ClassValueType
                || left instanceof MetaObjectType && right instanceof MetaObjectType
                || left instanceof AttributePathType && right instanceof AttributePathType
                || left instanceof StructureValueType && right instanceof StructureValueType)
            return equality?allowed("symbol-identity-comparable"):disallowed("symbol-not-ordered");
        if(left instanceof NumericType && right instanceof NumericType)return allowed("numeric-comparable");
        if(left instanceof BooleanType && right instanceof BooleanType)return allowed("ordered-boolean-comparable");
        if(left instanceof TextType && right instanceof TextType)
            return equality?allowed("text-equality-comparable"):disallowed("text-not-ordered");
        if(left instanceof AlignmentType a && right instanceof AlignmentType b && a.alignment()==b.alignment())return allowed("alignment-comparable");
        if(left instanceof EnumArgumentType && right instanceof EnumArgumentType && left.id().equals(right.id()))
            return equality?allowed("polymorphic-enum-equality"):disallowed("enum-order-not-specified");
        if(left instanceof OidType a && right instanceof OidType b && (a.oidKind()==b.oidKind() || a.oidKind()==OidKind.ANY || b.oidKind()==OidKind.ANY))
            return equality?allowed("oid-equality-comparable"):disallowed("oid-not-ordered");
        if(left instanceof EnumerationType a && right instanceof EnumerationType b && relatedEnumerations(a,b,resolve))
            return equality || a.ordered() && b.ordered() && !a.circular() && !b.circular()
                    ? allowed("enumeration-comparable"):disallowed("enumeration-not-ordered");
        if(left instanceof CoordinateType a && right instanceof CoordinateType b && a.dimensions()==b.dimensions() && a.multiCoordinate()==b.multiCoordinate())
            return equality?allowed("coordinate-equality-comparable"):disallowed("coordinate-not-ordered");
        if(left instanceof FormattedType a && right instanceof FormattedType b && formattedRelated(a,b,declaredType))return allowed("formatted-comparable");
        return disallowed("not-comparable");
    }

    private static boolean formattedRelated(FormattedType a, FormattedType b,
            java.util.function.Function<SymbolId,TypeDescriptor> declaredType) {
        if(formattedBaseContains(a,b.id(),declaredType) || formattedBaseContains(b,a.id(),declaredType))return true;
        var left=a.parts().stream().filter(p->p.attribute().isPresent()).toList();
        var right=b.parts().stream().filter(p->p.attribute().isPresent()).toList();
        if(left.isEmpty() || left.size()!=right.size())return false;
        for(int i=0;i<left.size();i++)
            if(!left.get(i).attribute().equals(right.get(i).attribute()) || !left.get(i).format().equals(right.get(i).format()))return false;
        return true;
    }

    private static boolean formattedBaseContains(FormattedType type, TypeId required,
            java.util.function.Function<SymbolId,TypeDescriptor> declaredType) {
        var seen=new java.util.HashSet<TypeId>();
        for(var current=type;current!=null && seen.add(current.id());) {
            if(current.id().equals(required))return true;
            var base=current.baseDomain().map(declaredType).orElse(null);
            current=base instanceof FormattedType formatted?formatted:null;
        }
        return false;
    }

    private static boolean formattedPrefix(FormattedType actual,FormattedType base,
            java.util.function.Function<SymbolId,SymbolId> baseOf,
            java.util.function.Function<SymbolId,TypeDescriptor> typeOf) {
        if(base.structure().isEmpty() || actual.structure().isEmpty()
                || !specializes(actual.structure().orElseThrow(),base.structure().orElseThrow(),baseOf)
                || actual.parts().size()<base.parts().size())return false;
        for(int i=0;i<base.parts().size();i++) {
            var a=actual.parts().get(i);var b=base.parts().get(i);
            if(a.attribute().isEmpty()!=b.attribute().isEmpty() || !a.width().equals(b.width()))return false;
            if(b.attribute().isEmpty()) {if(!a.literal().equals(b.literal()))return false;continue;}
            if(!specializes(a.attribute().orElseThrow(),b.attribute().orElseThrow(),baseOf))return false;
            if(a.format().isEmpty()!=b.format().isEmpty())return false;
            if(a.format().isPresent() && !a.format().equals(b.format())) {
                if(!(typeOf.apply(a.format().orElseThrow()) instanceof FormattedType x)
                        || !(typeOf.apply(b.format().orElseThrow()) instanceof FormattedType y)
                        || !formattedBaseContains(x,y.id(),typeOf))return false;
            }
        }
        return true;
    }

    private static boolean formattedContained(FormattedType source,FormattedType target,
            java.util.function.Function<SymbolId,TypeDescriptor> typeOf) {
        var values=new FormattedValues(typeOf,()->{});
        var s=values.interval(source);var t=values.interval(target);
        if(s.isEmpty() || t.isEmpty() || s.orElseThrow().components()<t.orElseThrow().components())return false;
        var a=s.orElseThrow();var b=t.orElseThrow();
        return (b.lower().isEmpty() || a.lower().isPresent() && FormattedValues.compare(a.lower().orElseThrow(),b.lower().orElseThrow())>=0)
                && (b.upper().isEmpty() || a.upper().isPresent() && FormattedValues.compare(a.upper().orElseThrow(),b.upper().orElseThrow())<=0);
    }

    private static boolean formattedAssignable(FormattedType source,FormattedType target,
            java.util.function.Function<TypeId,TypeDescriptor> resolve,
            java.util.function.Function<SymbolId,SymbolId> baseOf,
            java.util.function.Function<SymbolId,TypeDescriptor> typeOf) {
        boolean layout;
        if(formattedBaseContains(source,target.id(),typeOf))layout=formattedPrefix(source,target,baseOf,typeOf);
        else {
            var a=source.parts().stream().filter(p->p.attribute().isPresent()).toList();
            var b=target.parts().stream().filter(p->p.attribute().isPresent()).toList();
            layout=source.structure().isPresent() && source.structure().equals(target.structure()) && !a.isEmpty() && a.size()==b.size();
            for(int i=0;layout && i<a.size();i++)layout=a.get(i).attribute().equals(b.get(i).attribute()) && a.get(i).format().equals(b.get(i).format());
        }
        return layout && formattedContained(source,target,typeOf);
    }

    /** Coordinate specialization may fill unspecified units/reference systems, never replace a specified one. */
    public static boolean coordinateRefines(CoordinateType actual, CoordinateType base) {
        if (actual.dimensions() != base.dimensions() || actual.multiCoordinate() != base.multiCoordinate()
                || base.referenceSystem().isPresent() && !actual.referenceSystem().equals(base.referenceSystem())
                || base.rotation().isPresent() && !actual.rotation().equals(base.rotation())) return false;
        for (int i = 0; i < base.dimensions(); i++) {
            NumericRange a = actual.axes().get(i), b = base.axes().get(i);
            if (!contained(a.minimum(), b.minimum(), true) || !contained(a.maximum(), b.maximum(), false)
                    || b.unit().isPresent() && !a.unit().equals(b.unit())
                    || b.referenceSystem().isPresent() && !a.referenceSystem().equals(b.referenceSystem())
                    || a.circular() != b.circular() || a.direction() != b.direction()) return false;
        }
        return true;
    }

    private static boolean isSingleObject(TypeDescriptor type) {
        return type instanceof ReferenceType || type instanceof ObjectArgumentType o && !o.objects();
    }

    public static TypeRelation assign(TypeDescriptor source, TypeDescriptor target,
                                      java.util.function.Function<TypeId, TypeDescriptor> resolve) {
        return assign(source, target, resolve, ignored -> null);
    }

    public static TypeRelation assign(TypeDescriptor source, TypeDescriptor target,
                                      java.util.function.Function<TypeId, TypeDescriptor> resolve,
                                      java.util.function.Function<SymbolId, SymbolId> baseOf) {
        return assign(source,target,resolve,baseOf,ignored->null);
    }

    /** Domain descriptors are needed for geometric vertex specialization; callers supply their existing graph lookup. */
    public static TypeRelation assign(TypeDescriptor source, TypeDescriptor target,
            java.util.function.Function<TypeId,TypeDescriptor> resolve,
            java.util.function.Function<SymbolId,SymbolId> baseOf,
            java.util.function.Function<SymbolId,TypeDescriptor> domainType) {
        return assign(source,target,resolve,baseOf,domainType,AssignmentContext.NORMAL);
    }

    /** Function arguments may view an object as its inherited structure, unlike
     * ordinary embedded value assignment. Context is explicit and shared. */
    public static TypeRelation assign(TypeDescriptor source, TypeDescriptor target,
            java.util.function.Function<TypeId,TypeDescriptor> resolve,
            java.util.function.Function<SymbolId,SymbolId> baseOf,
            java.util.function.Function<SymbolId,TypeDescriptor> domainType, AssignmentContext context) {
        java.util.Objects.requireNonNull(context,"context");
        if (source == null || target == null || source instanceof ErrorType || target instanceof ErrorType) {
            return disallowed("unresolved-type");
        }
        if (source.id().equals(target.id())) {
            return allowed("same-type");
        }
        if(isUndefined(source) && !(target instanceof PendingType) && !(target instanceof GenericType generic && !generic.name().equals("UNDEFINED")))
            return allowed("undefined-value");
        if(target instanceof EnumArgumentType expected) {
            boolean fits=source instanceof EnumerationType enumeration && (expected.treeValues() || !enumeration.treeValues())
                    || source instanceof BooleanType || source instanceof AlignmentType
                    || source instanceof EnumArgumentType formal && (expected.treeValues() || !formal.treeValues());
            return fits?allowed("enumeration-argument"):disallowed("not-an-enumeration-argument");
        }
        if (source instanceof MetaObjectType s && target instanceof MetaObjectType t)
            return t.target().isEmpty() || s.target().isPresent() && specializes(s.target().get(),t.target().get(),baseOf)
                    ? allowed("metaobject-target-contained") : disallowed("metaobject-target-widens");
        if (target instanceof ObjectArgumentType t) {
            if (source instanceof ObjectArgumentType object && object.objects() && !t.objects()) {
                return disallowed("object-set-is-not-single-object");
            }
            List<SymbolId> actual;
            if (source instanceof ReferenceType reference) {
                actual = reference.targetRoots();
                if (actual.isEmpty()) return t.base().isEmpty() && t.restrictions().isEmpty()
                        ? allowed("any-object") : disallowed("object-target-widens");
            } else if (source instanceof ObjectArgumentType object) {
                actual = object.restrictions().isEmpty()
                        ? object.base().stream().toList() : object.restrictions();
                if (actual.isEmpty()) {
                    return t.base().isEmpty() && t.restrictions().isEmpty()
                            ? allowed("any-object") : disallowed("object-target-widens");
                }
            } else {
                return disallowed("not-an-object");
            }
            boolean fits = actual.stream().allMatch(candidate ->
                    t.base().map(base -> specializes(candidate, base, baseOf)).orElse(true)
                    && (t.restrictions().isEmpty() || t.restrictions().stream()
                    .anyMatch(restriction -> specializes(candidate, restriction, baseOf))));
            return fits ? allowed("object-target-contained") : disallowed("object-target-widens");
        }
        if (source instanceof EnumerationType s && target instanceof EnumerationType t)
            return enumerationExtends(s, t, resolve) ? allowed("enumeration-specialization") : disallowed("enumeration-target-differs");
        if (source instanceof NumericType s && target instanceof NumericType t) {
            return numericContained(s, t) ? allowed("numeric-range-contained")
                    : disallowed("numeric-range-widens");
        }
        if (source instanceof FormattedType s && target instanceof FormattedType t)
            return formattedAssignable(s,t,resolve,baseOf,domainType) ? allowed("formatted-range-contained") : disallowed("formatted-layout-or-range-widens");
        if (source instanceof TextType s && target instanceof TextType t) {
            boolean kindCompatible = s.textKind() == t.textKind()
                    || t.textKind() == TextKind.MTEXT
                    || t.textKind() == TextKind.TEXT && s.textKind() != TextKind.MTEXT;
            if (!kindCompatible) {
                return disallowed("text-kind-differs");
            }
            if (t.maxLength().isPresent()
                    && (s.maxLength().isEmpty() || s.maxLength().getAsInt() > t.maxLength().getAsInt())) {
                return disallowed("text-length-widens");
            }
            return allowed("text-compatible");
        }
        if (source instanceof BooleanType && target instanceof BooleanType) {
            return allowed("boolean-compatible");
        }
        if (source instanceof AttributePathType s && target instanceof AttributePathType t) {
            if (t.owner().isPresent() && !s.owner().equals(t.owner())) return disallowed("attribute-path-owner-differs");
            if (t.restrictions().isEmpty()) return allowed("unrestricted-attribute-path");
            boolean fits = !s.restrictions().isEmpty() && s.restrictions().stream().allMatch(a ->
                    t.restrictions().stream().anyMatch(b -> assign(resolve.apply(a), resolve.apply(b), resolve, baseOf, domainType).decision() == RelationDecision.ALLOWED));
            return fits ? allowed("attribute-path-type-contained") : disallowed("attribute-path-type-widens");
        }
        if (source instanceof ClassValueType s && target instanceof ClassValueType t) {
            if (s.structureValue() && !t.structureValue()) return disallowed("structure-value-is-not-class");
            if (t.restrictions().isEmpty()) return allowed("unrestricted-class-value");
            boolean contained = !s.restrictions().isEmpty() && s.restrictions().stream().allMatch(a ->
                    t.restrictions().stream().anyMatch(b -> specializes(a, b, baseOf)));
            return contained ? allowed("class-value-restriction-contained") : disallowed("class-value-restriction-widens");
        }
        if (target instanceof StructureValueType t) {
            List<SymbolId> actual;
            if(source instanceof StructureValueType structure) actual=structure.targetRoots();
            else if(context==AssignmentContext.FUNCTION_ARGUMENT && source instanceof ReferenceType reference) actual=reference.targetRoots();
            else if(context==AssignmentContext.FUNCTION_ARGUMENT && source instanceof ObjectArgumentType object && !object.objects())
                actual=object.restrictions().isEmpty()?object.base().stream().toList():object.restrictions();
            else return disallowed("not-a-structure-value");
            boolean fits=t.targetRoots().isEmpty() || !actual.isEmpty() && actual.stream()
                    .allMatch(a->t.targetRoots().stream().anyMatch(b->specializes(a,b,baseOf)));
            return fits?allowed("structure-specialization"):disallowed("structure-target-differs");
        }
        if (source instanceof ReferenceType s && target instanceof ReferenceType t) {
            boolean fits = t.targetRoots().isEmpty() || !s.targetRoots().isEmpty()
                    && s.targetRoots().stream().allMatch(a -> t.targetRoots().stream()
                    .anyMatch(b -> specializes(a, b, baseOf)));
            return fits ? allowed("reference-specialization") : disallowed("reference-target-differs");
        }
        if (source instanceof CoordinateType s && target instanceof CoordinateType t)
            return coordinateRefines(s,t) ? allowed("coordinate-contained") : disallowed("coordinate-axis-widens");
        if (source instanceof GeometryType s && target instanceof GeometryType t) {
            boolean family=s.geometryKind()==t.geometryKind()
                    || s.geometryKind()==GeometryKind.AREA && t.geometryKind()==GeometryKind.SURFACE
                    || s.geometryKind()==GeometryKind.MULTIAREA && t.geometryKind()==GeometryKind.MULTISURFACE;
            if(!family || t.directed() && !s.directed()
                    || !t.lineForms().isEmpty() && (s.lineForms().isEmpty() || !t.lineForms().containsAll(s.lineForms()))
                    || t.lineAttributes().isPresent() && !s.lineAttributes().equals(t.lineAttributes())
                    || t.withoutOverlaps() && !s.withoutOverlaps()
                    || t.overlap().isPresent() && !toleranceContained(s.overlap(), t.overlap(), domainType))
                return disallowed("geometry-definition-widens");
            if(t.vertexDomain().isPresent() && !s.vertexDomain().equals(t.vertexDomain())) {
                if(s.vertexDomain().isEmpty()) return disallowed("geometry-vertex-missing");
                var a=domainType.apply(s.vertexDomain().get()); var b=domainType.apply(t.vertexDomain().get());
                if(!(a instanceof CoordinateType ac && b instanceof CoordinateType bc) || !coordinateRefines(ac,bc))
                    return disallowed("geometry-vertex-widens");
            }
            return allowed("geometry-contained");
        }
        if (source instanceof CollectionType s && target instanceof CollectionType t) {
            if (s.collectionKind() != t.collectionKind() && t.collectionKind() != CollectionKind.BAG) {
                return disallowed("collection-kind-differs");
            }
            TypeDescriptor se = resolve.apply(s.elementType());
            TypeDescriptor te = resolve.apply(t.elementType());
            TypeRelation element = assign(se, te, resolve, baseOf, domainType, context);
            if (element.decision() != RelationDecision.ALLOWED) {
                return disallowed("collection-element-" + element.reason());
            }
            if (s.cardinality().lower().compareTo(t.cardinality().lower()) < 0
                    || upperCompare(s, t) > 0) {
                return disallowed("collection-cardinality-widens");
            }
            return allowed("collection-compatible");
        }
        return disallowed("incompatible-type-families");
    }

    /** Shared identity-based enum relation, including inline attribute specializations. */
    public static boolean relatedEnumerations(EnumerationType a, EnumerationType b,
            java.util.function.Function<TypeId, TypeDescriptor> resolve) {
        return enumerationExtends(a, b, resolve) || enumerationExtends(b, a, resolve);
    }

    private static boolean enumerationExtends(EnumerationType source, EnumerationType target,
            java.util.function.Function<TypeId, TypeDescriptor> resolve) {
        if(source.treeValues() && !target.treeValues())return false;
        TypeId required=target.treeValues()?target.baseType().orElse(target.id()):target.id();
        var seen = new java.util.HashSet<TypeId>();
        TypeDescriptor current = source;
        while (current instanceof EnumerationType e && seen.add(e.id())) {
            if (e.id().equals(required)) return true;
            current = e.baseType().map(resolve).orElse(null);
        }
        return false;
    }

    /** Cycle-safe identity-based specialization, shared by binding and public queries. */
    public static boolean specializes(SymbolId source, SymbolId target,
                                      java.util.function.Function<SymbolId, SymbolId> baseOf) {
        var seen = new java.util.HashSet<SymbolId>();
        for (SymbolId current = source; current != null && seen.add(current); current = baseOf.apply(current)) {
            if (current.equals(target)) return true;
        }
        return false;
    }
    private static int upperCompare(CollectionType s, CollectionType t) {
        if (t.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return 0;
        }
        if (s.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return 1;
        }
        var sf = (guru.interlis.model.CardinalityUpper.Finite) s.cardinality().upper();
        var tf = (guru.interlis.model.CardinalityUpper.Finite) t.cardinality().upper();
        return sf.value().compareTo(tf.value());
    }

    private static boolean numericContained(NumericType source, NumericType target) {
        return contained(source.range().minimum(), target.range().minimum(), true)
                && contained(source.range().maximum(), target.range().maximum(), false);
    }

    private static boolean contained(java.util.Optional<guru.interlis.model.DecimalLiteral> value,
                                     java.util.Optional<guru.interlis.model.DecimalLiteral> bound,
                                     boolean minimum) {
        if (bound.isEmpty()) {
            return true;
        }
        if (value.isEmpty()) {
            return false;
        }
        BigDecimal v = value.get().exactValue();
        BigDecimal b = bound.get().exactValue();
        return minimum ? v.compareTo(b) >= 0 : v.compareTo(b) <= 0;
    }

    private static TypeRelation allowed(String reason) {
        return new TypeRelation(RelationDecision.ALLOWED, reason, List.of());
    }

    private static TypeRelation disallowed(String reason) {
        return new TypeRelation(RelationDecision.DISALLOWED, reason, List.of());
    }
}
