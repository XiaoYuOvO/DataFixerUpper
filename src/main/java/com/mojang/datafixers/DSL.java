// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.mojang.datafixers;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Pool;
import com.mojang.datafixers.util.Unit;
import com.mojang.datafixers.kinds.App2;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Func;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.constant.BoolType;
import com.mojang.datafixers.types.constant.ByteType;
import com.mojang.datafixers.types.constant.DoubleType;
import com.mojang.datafixers.types.constant.FloatType;
import com.mojang.datafixers.types.constant.IntType;
import com.mojang.datafixers.types.constant.LongType;
import com.mojang.datafixers.types.constant.NamespacedStringType;
import com.mojang.datafixers.types.constant.NilDrop;
import com.mojang.datafixers.types.constant.NilSave;
import com.mojang.datafixers.types.constant.ShortType;
import com.mojang.datafixers.types.constant.StringType;
import com.mojang.datafixers.types.templates.Check;
import com.mojang.datafixers.types.templates.CompoundList;
import com.mojang.datafixers.types.templates.Const;
import com.mojang.datafixers.types.templates.Hook;
import com.mojang.datafixers.types.templates.List;
import com.mojang.datafixers.types.templates.Named;
import com.mojang.datafixers.types.templates.Product;
import com.mojang.datafixers.types.templates.RecursivePoint;
import com.mojang.datafixers.types.templates.Sum;
import com.mojang.datafixers.types.templates.Tag;
import com.mojang.datafixers.types.templates.TaggedChoice;
import com.mojang.datafixers.types.templates.TypeTemplate;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface DSL {
    interface TypeReference {
        String typeName();

        default TypeTemplate in(final Schema schema) {
            return schema.id(typeName());
        }
    }

    // Type/Template Factories

    static Type<Boolean> bool() {
        return Instances.BOOL_TYPE;
    }

    static Type<Integer> intType() {
        return Instances.INT_TYPE;
    }

    static Type<Long> longType() {
        return Instances.LONG_TYPE;
    }

    static Type<Byte> byteType() {
        return Instances.BYTE_TYPE;
    }

    static Type<Short> shortType() {
        return Instances.SHORT_TYPE;
    }

    static Type<Float> floatType() {
        return Instances.FLOAT_TYPE;
    }

    static Type<Double> doubleType() {
        return Instances.DOUBLE_TYPE;
    }

    static Type<String> string() {
        return Instances.STRING_TYPE;
    }

    static Type<String> namespacedString() {
        return Instances.NAMESPACED_STRING_TYPE;
    }

    static TypeTemplate nil() {
        return Instances.NIL_DROP_CONST;
    }

    static Type<Unit> nilType() {
        return Instances.NIL_DROP;
    }

    static TypeTemplate remainder() {
        return Instances.NIL_SAVE_CONST;
    }

    static Type<Dynamic<?>> remainderType() {
        return Instances.NIL_SAVE;
    }

    static TypeTemplate check(final String name, final int index, final TypeTemplate element) {
        return Pool.CHECK_POOL.create(new Check.CreateInfo(name, index, element));
    }

    static TypeTemplate compoundList(final TypeTemplate element) {
        return compoundList(Instances.STRING_CONST, element);
    }

    static <V> CompoundList.CompoundListType<String, V> compoundList(final Type<V> value) {
        return compoundList(string(), value);
    }

    static TypeTemplate compoundList(final TypeTemplate key, final TypeTemplate element) {
        return and(Pool.COMPOUND_LIST_POOL.create(new CompoundList.CreateInfo(key, element)), remainder());
    }

    static <K, V> CompoundList.CompoundListType<K, V> compoundList(final Type<K> key, final Type<V> value) {
        return new CompoundList.CompoundListType<>(key, value);
    }

    static TypeTemplate constType(final Type<?> type) {
        return Pool.CONST_POOL.create(new Const.CreateInfo(type));
    }

    static TypeTemplate hook(final TypeTemplate template, final Hook.HookFunction preRead, final Hook.HookFunction postWrite) {
        return Pool.HOOK_POOL.create(new Hook.CreateInfo(template, preRead, postWrite));
    }

    static <A> Type<A> hook(final Type<A> type, final Hook.HookFunction preRead, final Hook.HookFunction postWrite) {
        return new Hook.HookType<>(type, preRead, postWrite);
    }

    static TypeTemplate list(final TypeTemplate element) {
        return Pool.LIST_POOL.create(new List.CreateInfo(element));
    }

    static <A> List.ListType<A> list(final Type<A> first) {
        return new List.ListType<>(first);
    }

    static TypeTemplate named(final String name, final TypeTemplate element) {
        return Pool.NAMED_POOL.create(new Named.CreateInfo(name, element));
    }

    static <A> Type<Pair<String, A>> named(final String name, final Type<A> element) {
        return new Named.NamedType<>(name, element);
    }

    static TypeTemplate and(final TypeTemplate first, final TypeTemplate second) {
        return Pool.PRODUCT_POOL.create(new Product.CreateInfo(first, second));
    }

    static TypeTemplate and(final TypeTemplate first, final TypeTemplate... rest) {
        if (rest.length == 0) {
            return first;
        }
        TypeTemplate result = rest[rest.length - 1];
        for (int i = rest.length - 2; i >= 0; i--) {
            result = and(rest[i], result);
        }
        return and(first, result);
    }

    static TypeTemplate allWithRemainder(final TypeTemplate first, final TypeTemplate... rest) {
        return and(first, ArrayUtils.add(rest, remainder()));
    }

    static <F, G> Type<Pair<F, G>> and(final Type<F> first, final Type<G> second) {
        return new Product.ProductType<>(first, second);
    }

    static <F, G, H> Type<Pair<F, Pair<G, H>>> and(final Type<F> first, final Type<G> second, final Type<H> third) {
        return and(first, and(second, third));
    }

    static <F, G, H, I> Type<Pair<F, Pair<G, Pair<H, I>>>> and(final Type<F> first, final Type<G> second, final Type<H> third, final Type<I> forth) {
        return and(first, and(second, and(third, forth)));
    }

    static TypeTemplate id(final int index) {
        return Pool.RECURSIVE_POINT_POOL.create(new RecursivePoint.CreateInfo(index));
    }

    static TypeTemplate or(final TypeTemplate left, final TypeTemplate right) {
        return Pool.SUM_POOL.create(new Sum.CreateInfo(left, right));
    }

    static <F, G> Type<Either<F, G>> or(final Type<F> first, final Type<G> second) {
        return new Sum.SumType<>(first, second);
    }

    static TypeTemplate field(final String name, final TypeTemplate element) {
        return Pool.TAG_POOL.create(new Tag.CreateInfo(name, element));
    }

    static <A> Tag.TagType<A> field(final String name, final Type<A> element) {
        return new Tag.TagType<>(name, element);
    }

    @SuppressWarnings("unchecked")
    static <K> TaggedChoice<K> taggedChoice(final String name, final Type<K> keyType, final Object2ObjectMap<K, TypeTemplate> templates) {
        return (TaggedChoice<K>) Pool.TAGGED_CHOICE_POOL.create(new TaggedChoice.CreateInfo<>(name, keyType, templates));
    }

    static <K> TaggedChoice<K> taggedChoiceLazy(final String name, final Type<K> keyType, final Map<K, Supplier<TypeTemplate>> templates) {
        Object2ObjectMap<K, TypeTemplate> map = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<K, Supplier<TypeTemplate>> e : templates.entrySet()) {
            if (map.put(e.getKey(), e.getValue().get())!=null) {
                throw new IllegalStateException("Duplicate key");
            }
        }
        return taggedChoice(name, keyType, map);
    }

    @SuppressWarnings("unchecked")
    static <K> Type<Pair<K, ?>> taggedChoiceType(final String name, final Type<K> keyType, final Object2ObjectMap<K, Type<?>> types) {
        return (Type<Pair<K, ?>>) Instances.TAGGED_CHOICE_TYPE_CACHE.computeIfAbsent(Triple.of(name, keyType, types), k -> {
            return new TaggedChoice.TaggedChoiceType<>(k.getLeft(), (Type<K>) k.getMiddle(), (Object2ObjectMap<K, Type<?>>) k.getRight());
        });
    }

    static <A, B> Type<Function<A, B>> func(final Type<A> input, final Type<B> output) {
        return new Func<>(input, output);
    }

    // Helpers

    static <A> Type<Either<A, Unit>> optional(final Type<A> type) {
        return or(type, nilType());
    }

    static TypeTemplate optional(final TypeTemplate value) {
        return or(value, nil());
    }

    static TypeTemplate fields(
        final String name1, final TypeTemplate element1
    ) {
        return allWithRemainder(
            field(name1, element1)
        );
    }

    static TypeTemplate fields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2
    ) {
        return allWithRemainder(
            field(name1, element1),
            field(name2, element2)
        );
    }

    static TypeTemplate fields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3
    ) {
        return allWithRemainder(
            field(name1, element1),
            field(name2, element2),
            field(name3, element3)
        );
    }

    static TypeTemplate fields(
        final String name, final TypeTemplate element,
        final TypeTemplate rest
    ) {
        return and(
            field(name, element),
            rest
        );
    }

    static TypeTemplate fields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final TypeTemplate rest
    ) {
        return and(
            field(name1, element1),
            field(name2, element2),
            rest
        );
    }

    static TypeTemplate fields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final TypeTemplate rest
    ) {
        return and(
            field(name1, element1),
            field(name2, element2),
            field(name3, element3),
            rest
        );
    }

    static TypeTemplate optionalFields(final String name, final TypeTemplate element) {
        return allWithRemainder(
            optional(field(name, element))
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2
    ) {
        return allWithRemainder(
            optional(field(name1, element1)),
            optional(field(name2, element2))
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3
    ) {
        return allWithRemainder(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3))
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final String name4, final TypeTemplate element4
    ) {
        return allWithRemainder(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3)),
            optional(field(name4, element4))
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final String name4, final TypeTemplate element4,
        final String name5, final TypeTemplate element5
    ) {
        return allWithRemainder(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3)),
            optional(field(name4, element4)),
            optional(field(name5, element5))
        );
    }

    static TypeTemplate optionalFields(
        final String name, final TypeTemplate element,
        final TypeTemplate rest
    ) {
        return and(
            optional(field(name, element)),
            rest
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final TypeTemplate rest
    ) {
        return and(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            rest
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final TypeTemplate rest
    ) {
        return and(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3)),
            rest
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final String name4, final TypeTemplate element4,
        final TypeTemplate rest
    ) {
        return and(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3)),
            optional(field(name4, element4)),
            rest
        );
    }

    static TypeTemplate optionalFields(
        final String name1, final TypeTemplate element1,
        final String name2, final TypeTemplate element2,
        final String name3, final TypeTemplate element3,
        final String name4, final TypeTemplate element4,
        final String name5, final TypeTemplate element5,
        final TypeTemplate rest
    ) {
        return and(
            optional(field(name1, element1)),
            optional(field(name2, element2)),
            optional(field(name3, element3)),
            optional(field(name4, element4)),
            optional(field(name5, element5)),
            rest
        );
    }

    // Type matchers

    static OpticFinder<Dynamic<?>> remainderFinder() {
        return Instances.REMAINDER_FINDER;
    }

    static <FT> OpticFinder<FT> typeFinder(final Type<FT> type) {
        return new FieldFinder<>(null, type);
    }

    static <FT> OpticFinder<FT> fieldFinder(final String name, final Type<FT> type) {
        return new FieldFinder<>(name, type);
    }

    static <FT> OpticFinder<FT> namedChoice(final String name, final Type<FT> type) {
        return new NamedChoiceFinder<>(name, type);
    }

    static Unit unit() {
        return null;
    }

    final class Instances {
        private static final Type<Boolean> BOOL_TYPE = new BoolType();
        private static final Type<Integer> INT_TYPE = new IntType();
        private static final Type<Long> LONG_TYPE = new LongType();
        private static final Type<Byte> BYTE_TYPE = new ByteType();
        private static final Type<Short> SHORT_TYPE = new ShortType();
        private static final Type<Float> FLOAT_TYPE = new FloatType();
        private static final Type<Double> DOUBLE_TYPE = new DoubleType();
        private static final Type<String> STRING_TYPE = new StringType();
        private static final Type<String> NAMESPACED_STRING_TYPE = new NamespacedStringType();
        private static final Type<Unit> NIL_DROP = new NilDrop();
        private static final Type<Dynamic<?>> NIL_SAVE = new NilSave();

        private static final TypeTemplate NIL_DROP_CONST = new Const(Instances.NIL_DROP);
        private static final TypeTemplate NIL_SAVE_CONST = new Const(Instances.NIL_SAVE);
        private static final TypeTemplate STRING_CONST = new Const(Instances.STRING_TYPE);

        private static final OpticFinder<Dynamic<?>> REMAINDER_FINDER = remainderType().finder();

        private static final Map<Triple<String, Type<?>, Map<?, Type<?>>>, Type<? extends Pair<?, ?>>> TAGGED_CHOICE_TYPE_CACHE = Maps.newConcurrentMap();
    }
}
