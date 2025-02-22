package ai.timefold.solver.core.impl.domain.valuerange.descriptor;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.CountableValueRange;
import ai.timefold.solver.core.api.domain.valuerange.ValueRange;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.config.util.ConfigUtils;
import ai.timefold.solver.core.impl.domain.common.ReflectionHelper;
import ai.timefold.solver.core.impl.domain.common.accessor.MemberAccessor;
import ai.timefold.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.timefold.solver.core.impl.domain.valuerange.buildin.collection.ListValueRange;
import ai.timefold.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public abstract class AbstractFromPropertyValueRangeDescriptor<Solution_>
        extends AbstractValueRangeDescriptor<Solution_> {

    protected final MemberAccessor memberAccessor;
    protected boolean collectionWrapping;
    protected boolean arrayWrapping;
    protected boolean countable;

    public AbstractFromPropertyValueRangeDescriptor(GenuineVariableDescriptor<Solution_> variableDescriptor,
            boolean addNullInValueRange,
            MemberAccessor memberAccessor) {
        super(variableDescriptor, addNullInValueRange);
        this.memberAccessor = memberAccessor;
        ValueRangeProvider valueRangeProviderAnnotation = memberAccessor.getAnnotation(ValueRangeProvider.class);
        if (valueRangeProviderAnnotation == null) {
            throw new IllegalStateException("The member (" + memberAccessor
                    + ") must have a valueRangeProviderAnnotation (" + valueRangeProviderAnnotation + ").");
        }
        processValueRangeProviderAnnotation(valueRangeProviderAnnotation);
        if (addNullInValueRange && !countable) {
            throw new IllegalStateException("The valueRangeDescriptor (" + this
                    + ") is nullable, but not countable (" + countable + ").\n"
                    + "Maybe the member (" + memberAccessor + ") should return "
                    + CountableValueRange.class.getSimpleName() + ".");
        }
    }

    private void processValueRangeProviderAnnotation(ValueRangeProvider valueRangeProviderAnnotation) {
        EntityDescriptor<Solution_> entityDescriptor = variableDescriptor.getEntityDescriptor();
        Class<?> type = memberAccessor.getType();
        collectionWrapping = Collection.class.isAssignableFrom(type);
        arrayWrapping = type.isArray();
        if (!collectionWrapping && !arrayWrapping && !ValueRange.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("The entityClass (" + entityDescriptor.getEntityClass()
                    + ") has a @" + PlanningVariable.class.getSimpleName()
                    + " annotated property (" + variableDescriptor.getVariableName()
                    + ") that refers to a @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") that does not return a " + Collection.class.getSimpleName()
                    + ", an array or a " + ValueRange.class.getSimpleName() + ".");
        }
        if (collectionWrapping) {
            Class<?> collectionElementClass = ConfigUtils.extractCollectionGenericTypeParameterStrictly(
                    "solutionClass or entityClass", memberAccessor.getDeclaringClass(),
                    memberAccessor.getType(), memberAccessor.getGenericType(),
                    ValueRangeProvider.class, memberAccessor.getName());
            if (!variableDescriptor.acceptsValueType(collectionElementClass)) {
                throw new IllegalArgumentException("The entityClass (" + entityDescriptor.getEntityClass()
                        + ") has a @" + PlanningVariable.class.getSimpleName()
                        + " annotated property (" + variableDescriptor.getVariableName()
                        + ") that refers to a @" + ValueRangeProvider.class.getSimpleName()
                        + " annotated member (" + memberAccessor
                        + ") that returns a " + Collection.class.getSimpleName()
                        + " with elements of type (" + collectionElementClass
                        + ") which cannot be assigned to the @" + PlanningVariable.class.getSimpleName()
                        + "'s type (" + variableDescriptor.getVariablePropertyType() + ").");
            }
        } else if (arrayWrapping) {
            Class<?> arrayElementClass = type.getComponentType();
            if (!variableDescriptor.acceptsValueType(arrayElementClass)) {
                throw new IllegalArgumentException("The entityClass (" + entityDescriptor.getEntityClass()
                        + ") has a @" + PlanningVariable.class.getSimpleName()
                        + " annotated property (" + variableDescriptor.getVariableName()
                        + ") that refers to a @" + ValueRangeProvider.class.getSimpleName()
                        + " annotated member (" + memberAccessor
                        + ") that returns an array with elements of type (" + arrayElementClass
                        + ") which cannot be assigned to the @" + PlanningVariable.class.getSimpleName()
                        + "'s type (" + variableDescriptor.getVariablePropertyType() + ").");
            }
        }
        countable = collectionWrapping || arrayWrapping || CountableValueRange.class.isAssignableFrom(type);
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    @Override
    public boolean isCountable() {
        return countable;
    }

    protected ValueRange<?> readValueRange(Object bean) {
        Object valueRangeObject = memberAccessor.executeGetter(bean);
        if (valueRangeObject == null) {
            throw new IllegalStateException("The @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") called on bean (" + bean
                    + ") must not return a null valueRangeObject (" + valueRangeObject + ").");
        }
        ValueRange<Object> valueRange;
        if (collectionWrapping || arrayWrapping) {
            List<Object> list = collectionWrapping ? transformCollectionToList((Collection<Object>) valueRangeObject)
                    : ReflectionHelper.transformArrayToList(valueRangeObject);
            // Don't check the entire list for performance reasons, but do check common pitfalls
            if (!list.isEmpty() && (list.get(0) == null || list.get(list.size() - 1) == null)) {
                throw new IllegalStateException("The @" + ValueRangeProvider.class.getSimpleName()
                        + " annotated member (" + memberAccessor
                        + ") called on bean (" + bean
                        + ") must not return a " + (collectionWrapping ? Collection.class.getSimpleName() : "array")
                        + "(" + list + ") with an element that is null.\n"
                        + "Maybe remove that null element from the dataset.\n"
                        + "Maybe use @" + PlanningVariable.class.getSimpleName() + "(nullable = true) instead.");
            }
            valueRange = new ListValueRange<>(list);
        } else {
            valueRange = (ValueRange<Object>) valueRangeObject;
        }
        valueRange = doNullInValueRangeWrapping(valueRange);
        if (valueRange.isEmpty()) {
            throw new IllegalStateException("The @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") called on bean (" + bean
                    + ") must not return an empty valueRange (" + valueRangeObject + ").\n"
                    + "Maybe apply overconstrained planning as described in the documentation.");
        }
        return valueRange;
    }

    protected long readValueRangeSize(Object bean) {
        Object valueRangeObject = memberAccessor.executeGetter(bean);
        if (valueRangeObject == null) {
            throw new IllegalStateException("The @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") called on bean (" + bean
                    + ") must not return a null valueRangeObject (" + valueRangeObject + ").");
        }
        int size = addNullInValueRange ? 1 : 0;
        if (collectionWrapping) {
            return size + ((Collection<Object>) valueRangeObject).size();
        } else if (arrayWrapping) {
            return size + Array.getLength(valueRangeObject);
        }
        ValueRange<Object> valueRange = (ValueRange<Object>) valueRangeObject;
        if (valueRange.isEmpty()) {
            throw new IllegalStateException("The @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") called on bean (" + bean
                    + ") must not return an empty valueRange (" + valueRangeObject + ").\n"
                    + "Maybe apply overconstrained planning as described in the documentation.");
        } else if (valueRange instanceof CountableValueRange<Object> countableValueRange) {
            return size + countableValueRange.getSize();
        } else {
            throw new UnsupportedOperationException("The @" + ValueRangeProvider.class.getSimpleName()
                    + " annotated member (" + memberAccessor
                    + ") called on bean (" + bean
                    + ") is not countable and therefore does not support getSize().");
        }
    }

    private <T> List<T> transformCollectionToList(Collection<T> collection) {
        if (collection instanceof List<T> list) {
            if (collection instanceof LinkedList<T> linkedList) {
                // ValueRange.createRandomIterator(Random) and ValueRange.get(int) wouldn't be efficient.
                return new ArrayList<>(linkedList);
            } else {
                return list;
            }
        } else {
            // TODO If only ValueRange.createOriginalIterator() is used, cloning a Set to a List is a waste of time.
            return new ArrayList<>(collection);
        }
    }

}
