# 差异分析报告（Java 端口 / 真实包比对）

- 老包：`lib_v1.jar`（版本 3.12.0）
- 新包：`lib_v2.jar`（版本 3.14.0）

## 一、差异统计
- 新增 **63** · 删除 **4** · 修改 **345** · 未变 1
- 业务/类级变更(非jar)：412 · jar 级变更：0

## 二、差异文件树（全量清单）
- `修改 ~` META-INF/MANIFEST.MF
- `修改 ~` META-INF/NOTICE.txt
- `修改 ~` META-INF/maven/org.apache.commons/commons-lang3/pom.properties
- `修改 ~` META-INF/maven/org.apache.commons/commons-lang3/pom.xml
- `修改 ~` org/apache/commons/lang3/AnnotationUtils$1.class
- `修改 ~` org/apache/commons/lang3/AnnotationUtils.class
- `修改 ~` org/apache/commons/lang3/ArchUtils.class
- `修改 ~` org/apache/commons/lang3/ArraySorter.class
- `修改 ~` org/apache/commons/lang3/ArrayUtils.class
- `修改 ~` org/apache/commons/lang3/BitField.class
- `修改 ~` org/apache/commons/lang3/BooleanUtils.class
- `修改 ~` org/apache/commons/lang3/CharEncoding.class
- `修改 ~` org/apache/commons/lang3/CharRange$1.class
- `修改 ~` org/apache/commons/lang3/CharRange$CharacterIterator.class
- `修改 ~` org/apache/commons/lang3/CharRange.class
- `修改 ~` org/apache/commons/lang3/CharSequenceUtils.class
- `修改 ~` org/apache/commons/lang3/CharSet.class
- `修改 ~` org/apache/commons/lang3/CharSetUtils.class
- `修改 ~` org/apache/commons/lang3/CharUtils.class
- `修改 ~` org/apache/commons/lang3/Charsets.class
- `修改 ~` org/apache/commons/lang3/ClassLoaderUtils.class
- `修改 ~` org/apache/commons/lang3/ClassPathUtils.class
- `修改 ~` org/apache/commons/lang3/ClassUtils$1.class
- `修改 ~` org/apache/commons/lang3/ClassUtils$2.class
- `修改 ~` org/apache/commons/lang3/ClassUtils$Interfaces.class
- `修改 ~` org/apache/commons/lang3/ClassUtils.class
- `修改 ~` org/apache/commons/lang3/Conversion.class
- `修改 ~` org/apache/commons/lang3/EnumUtils.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableBiConsumer.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableBiFunction.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableBiPredicate.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableCallable.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableConsumer.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableFunction.class
- `修改 ~` org/apache/commons/lang3/Functions$FailablePredicate.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableRunnable.class
- `修改 ~` org/apache/commons/lang3/Functions$FailableSupplier.class
- `修改 ~` org/apache/commons/lang3/Functions.class
- `修改 ~` org/apache/commons/lang3/JavaVersion.class
- `修改 ~` org/apache/commons/lang3/LocaleUtils$SyncAvoid.class
- `修改 ~` org/apache/commons/lang3/LocaleUtils.class
- `修改 ~` org/apache/commons/lang3/NotImplementedException.class
- `修改 ~` org/apache/commons/lang3/ObjectUtils$Null.class
- `修改 ~` org/apache/commons/lang3/ObjectUtils.class
- `修改 ~` org/apache/commons/lang3/RandomStringUtils.class
- `修改 ~` org/apache/commons/lang3/RandomUtils.class
- `修改 ~` org/apache/commons/lang3/Range$ComparableComparator.class
- `修改 ~` org/apache/commons/lang3/Range.class
- `修改 ~` org/apache/commons/lang3/RegExUtils.class
- `修改 ~` org/apache/commons/lang3/SerializationException.class
- `修改 ~` org/apache/commons/lang3/SerializationUtils$ClassLoaderAwareObjectInputStream.class
- `修改 ~` org/apache/commons/lang3/SerializationUtils.class
- `修改 ~` org/apache/commons/lang3/Streams$ArrayCollector.class
- `修改 ~` org/apache/commons/lang3/Streams$FailableStream.class
- `修改 ~` org/apache/commons/lang3/Streams.class
- `修改 ~` org/apache/commons/lang3/StringEscapeUtils$CsvEscaper.class
- `修改 ~` org/apache/commons/lang3/StringEscapeUtils$CsvUnescaper.class
- `修改 ~` org/apache/commons/lang3/StringEscapeUtils.class
- `修改 ~` org/apache/commons/lang3/StringUtils.class
- `修改 ~` org/apache/commons/lang3/SystemUtils.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$1.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$AlwaysTruePredicate.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$NamePredicate.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$ThreadGroupPredicate.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$ThreadIdPredicate.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils$ThreadPredicate.class
- `修改 ~` org/apache/commons/lang3/ThreadUtils.class
- `修改 ~` org/apache/commons/lang3/Validate.class
- `修改 ~` org/apache/commons/lang3/arch/Processor$Arch.class
- `修改 ~` org/apache/commons/lang3/arch/Processor$Type.class
- `修改 ~` org/apache/commons/lang3/arch/Processor.class
- `修改 ~` org/apache/commons/lang3/builder/Builder.class
- `修改 ~` org/apache/commons/lang3/builder/CompareToBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/Diff.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$1.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$10.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$11.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$12.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$13.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$14.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$15.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$16.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$17.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$18.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$2.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$3.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$4.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$5.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$6.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$7.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$8.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder$9.class
- `修改 ~` org/apache/commons/lang3/builder/DiffBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/DiffResult.class
- `修改 ~` org/apache/commons/lang3/builder/Diffable.class
- `修改 ~` org/apache/commons/lang3/builder/EqualsBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/EqualsExclude.class
- `修改 ~` org/apache/commons/lang3/builder/HashCodeBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/HashCodeExclude.class
- `修改 ~` org/apache/commons/lang3/builder/IDKey.class
- `修改 ~` org/apache/commons/lang3/builder/MultilineRecursiveToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/RecursiveToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ReflectionDiffBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/ReflectionToStringBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/StandardToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringBuilder.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringExclude.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$DefaultToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$JsonToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$MultiLineToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$NoClassNameToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$NoFieldNameToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$ShortPrefixToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle$SimpleToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringStyle.class
- `修改 ~` org/apache/commons/lang3/builder/ToStringSummary.class
- `修改 ~` org/apache/commons/lang3/compare/ComparableUtils$1.class
- `修改 ~` org/apache/commons/lang3/compare/ComparableUtils$ComparableCheckBuilder.class
- `修改 ~` org/apache/commons/lang3/compare/ComparableUtils.class
- `修改 ~` org/apache/commons/lang3/compare/ObjectToStringComparator.class
- `修改 ~` org/apache/commons/lang3/concurrent/AbstractCircuitBreaker$1.class
- `修改 ~` org/apache/commons/lang3/concurrent/AbstractCircuitBreaker$State$1.class
- `修改 ~` org/apache/commons/lang3/concurrent/AbstractCircuitBreaker$State$2.class
- `修改 ~` org/apache/commons/lang3/concurrent/AbstractCircuitBreaker$State.class
- `修改 ~` org/apache/commons/lang3/concurrent/AbstractCircuitBreaker.class
- `修改 ~` org/apache/commons/lang3/concurrent/AtomicInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/AtomicSafeInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/BackgroundInitializer$InitializationTask.class
- `修改 ~` org/apache/commons/lang3/concurrent/BackgroundInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/BasicThreadFactory$1.class
- `修改 ~` org/apache/commons/lang3/concurrent/BasicThreadFactory$Builder.class
- `修改 ~` org/apache/commons/lang3/concurrent/BasicThreadFactory.class
- `修改 ~` org/apache/commons/lang3/concurrent/CallableBackgroundInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/CircuitBreaker.class
- `修改 ~` org/apache/commons/lang3/concurrent/CircuitBreakingException.class
- `修改 ~` org/apache/commons/lang3/concurrent/Computable.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConcurrentException.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConcurrentInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConcurrentRuntimeException.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConcurrentUtils$ConstantFuture.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConcurrentUtils.class
- `修改 ~` org/apache/commons/lang3/concurrent/ConstantInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker$1.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker$CheckIntervalData.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker$StateStrategy.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker$StateStrategyClosed.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker$StateStrategyOpen.class
- `修改 ~` org/apache/commons/lang3/concurrent/EventCountCircuitBreaker.class
- `修改 ~` org/apache/commons/lang3/concurrent/LazyInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/Memoizer.class
- `修改 ~` org/apache/commons/lang3/concurrent/MultiBackgroundInitializer$1.class
- `修改 ~` org/apache/commons/lang3/concurrent/MultiBackgroundInitializer$MultiBackgroundInitializerResults.class
- `修改 ~` org/apache/commons/lang3/concurrent/MultiBackgroundInitializer.class
- `修改 ~` org/apache/commons/lang3/concurrent/ThresholdCircuitBreaker.class
- `修改 ~` org/apache/commons/lang3/concurrent/TimedSemaphore.class
- `修改 ~` org/apache/commons/lang3/concurrent/locks/LockingVisitors$LockVisitor.class
- `修改 ~` org/apache/commons/lang3/concurrent/locks/LockingVisitors$ReadWriteLockVisitor.class
- `修改 ~` org/apache/commons/lang3/concurrent/locks/LockingVisitors$StampedLockVisitor.class
- `修改 ~` org/apache/commons/lang3/concurrent/locks/LockingVisitors.class
- `修改 ~` org/apache/commons/lang3/event/EventListenerSupport$ProxyInvocationHandler.class
- `修改 ~` org/apache/commons/lang3/event/EventListenerSupport.class
- `修改 ~` org/apache/commons/lang3/event/EventUtils$EventBindingInvocationHandler.class
- `修改 ~` org/apache/commons/lang3/event/EventUtils.class
- `修改 ~` org/apache/commons/lang3/exception/CloneFailedException.class
- `修改 ~` org/apache/commons/lang3/exception/ContextedException.class
- `修改 ~` org/apache/commons/lang3/exception/ContextedRuntimeException.class
- `修改 ~` org/apache/commons/lang3/exception/DefaultExceptionContext.class
- `修改 ~` org/apache/commons/lang3/exception/ExceptionContext.class
- `修改 ~` org/apache/commons/lang3/exception/ExceptionUtils.class
- `修改 ~` org/apache/commons/lang3/function/Failable.class
- `修改 ~` org/apache/commons/lang3/function/FailableBiConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableBiFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableBiPredicate.class
- `修改 ~` org/apache/commons/lang3/function/FailableBooleanSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableCallable.class
- `修改 ~` org/apache/commons/lang3/function/FailableConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleBinaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoublePredicate.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleToIntFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleToLongFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableDoubleUnaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntBinaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntPredicate.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntToDoubleFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntToLongFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableIntUnaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongBinaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongPredicate.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongToDoubleFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongToIntFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableLongUnaryOperator.class
- `修改 ~` org/apache/commons/lang3/function/FailableObjDoubleConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableObjIntConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailableObjLongConsumer.class
- `修改 ~` org/apache/commons/lang3/function/FailablePredicate.class
- `修改 ~` org/apache/commons/lang3/function/FailableRunnable.class
- `修改 ~` org/apache/commons/lang3/function/FailableShortSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableSupplier.class
- `修改 ~` org/apache/commons/lang3/function/FailableToDoubleBiFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableToDoubleFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableToIntBiFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableToIntFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableToLongBiFunction.class
- `修改 ~` org/apache/commons/lang3/function/FailableToLongFunction.class
- `修改 ~` org/apache/commons/lang3/function/ToBooleanBiFunction.class
- `修改 ~` org/apache/commons/lang3/function/TriFunction.class
- `修改 ~` org/apache/commons/lang3/math/Fraction.class
- `修改 ~` org/apache/commons/lang3/math/IEEE754rUtils.class
- `修改 ~` org/apache/commons/lang3/math/NumberUtils.class
- `修改 ~` org/apache/commons/lang3/mutable/Mutable.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableBoolean.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableByte.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableDouble.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableFloat.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableInt.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableLong.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableObject.class
- `修改 ~` org/apache/commons/lang3/mutable/MutableShort.class
- `修改 ~` org/apache/commons/lang3/reflect/ConstructorUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/FieldUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/InheritanceUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/MemberUtils$Executable.class
- `修改 ~` org/apache/commons/lang3/reflect/MemberUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/MethodUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeLiteral.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils$1.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils$GenericArrayTypeImpl.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils$ParameterizedTypeImpl.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils$WildcardTypeBuilder.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils$WildcardTypeImpl.class
- `修改 ~` org/apache/commons/lang3/reflect/TypeUtils.class
- `修改 ~` org/apache/commons/lang3/reflect/Typed.class
- `修改 ~` org/apache/commons/lang3/stream/Streams$ArrayCollector.class
- `修改 ~` org/apache/commons/lang3/stream/Streams$FailableStream.class
- `修改 ~` org/apache/commons/lang3/stream/Streams.class
- `修改 ~` org/apache/commons/lang3/text/CompositeFormat.class
- `修改 ~` org/apache/commons/lang3/text/ExtendedMessageFormat.class
- `修改 ~` org/apache/commons/lang3/text/FormatFactory.class
- `修改 ~` org/apache/commons/lang3/text/FormattableUtils.class
- `修改 ~` org/apache/commons/lang3/text/StrBuilder$StrBuilderReader.class
- `修改 ~` org/apache/commons/lang3/text/StrBuilder$StrBuilderTokenizer.class
- `修改 ~` org/apache/commons/lang3/text/StrBuilder$StrBuilderWriter.class
- `修改 ~` org/apache/commons/lang3/text/StrBuilder.class
- `修改 ~` org/apache/commons/lang3/text/StrLookup$1.class
- `修改 ~` org/apache/commons/lang3/text/StrLookup$MapStrLookup.class
- `修改 ~` org/apache/commons/lang3/text/StrLookup$SystemPropertiesStrLookup.class
- `修改 ~` org/apache/commons/lang3/text/StrLookup.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher$CharMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher$CharSetMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher$NoMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher$StringMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher$TrimMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrMatcher.class
- `修改 ~` org/apache/commons/lang3/text/StrSubstitutor.class
- `修改 ~` org/apache/commons/lang3/text/StrTokenizer.class
- `修改 ~` org/apache/commons/lang3/text/WordUtils.class
- `修改 ~` org/apache/commons/lang3/text/translate/AggregateTranslator.class
- `修改 ~` org/apache/commons/lang3/text/translate/CharSequenceTranslator.class
- `修改 ~` org/apache/commons/lang3/text/translate/CodePointTranslator.class
- `修改 ~` org/apache/commons/lang3/text/translate/EntityArrays.class
- `修改 ~` org/apache/commons/lang3/text/translate/JavaUnicodeEscaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/LookupTranslator.class
- `修改 ~` org/apache/commons/lang3/text/translate/NumericEntityEscaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/NumericEntityUnescaper$OPTION.class
- `修改 ~` org/apache/commons/lang3/text/translate/NumericEntityUnescaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/OctalUnescaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/UnicodeEscaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/UnicodeUnescaper.class
- `修改 ~` org/apache/commons/lang3/text/translate/UnicodeUnpairedSurrogateRemover.class
- `修改 ~` org/apache/commons/lang3/time/CalendarUtils.class
- `修改 ~` org/apache/commons/lang3/time/DateFormatUtils.class
- `修改 ~` org/apache/commons/lang3/time/DateParser.class
- `修改 ~` org/apache/commons/lang3/time/DatePrinter.class
- `修改 ~` org/apache/commons/lang3/time/DateUtils$DateIterator.class
- `修改 ~` org/apache/commons/lang3/time/DateUtils$ModifyType.class
- `修改 ~` org/apache/commons/lang3/time/DateUtils.class
- `修改 ~` org/apache/commons/lang3/time/DurationFormatUtils$Token.class
- `修改 ~` org/apache/commons/lang3/time/DurationFormatUtils.class
- `修改 ~` org/apache/commons/lang3/time/DurationUtils$1.class
- `修改 ~` org/apache/commons/lang3/time/DurationUtils.class
- `修改 ~` org/apache/commons/lang3/time/FastDateFormat$1.class
- `修改 ~` org/apache/commons/lang3/time/FastDateFormat.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$1.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$2.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$3.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$4.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$5.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$CaseInsensitiveTextStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$CopyQuotedStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$ISO8601TimeZoneStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$NumberStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$PatternStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$Strategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$StrategyAndWidth.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$StrategyParser.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$TimeZoneStrategy$TzInfo.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser$TimeZoneStrategy.class
- `修改 ~` org/apache/commons/lang3/time/FastDateParser.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$CharacterLiteral.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$DayInWeekField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$Iso8601_Rule.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$NumberRule.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$PaddedNumberField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$Rule.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$StringLiteral.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TextField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TimeZoneDisplayKey.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TimeZoneNameRule.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TimeZoneNumberRule.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TwelveHourField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TwentyFourHourField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TwoDigitMonthField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TwoDigitNumberField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$TwoDigitYearField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$UnpaddedMonthField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$UnpaddedNumberField.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter$WeekYear.class
- `修改 ~` org/apache/commons/lang3/time/FastDatePrinter.class
- `修改 ~` org/apache/commons/lang3/time/FastTimeZone.class
- `修改 ~` org/apache/commons/lang3/time/GmtTimeZone.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$1.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$SplitState.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$State$1.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$State$2.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$State$3.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$State$4.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch$State.class
- `修改 ~` org/apache/commons/lang3/time/StopWatch.class
- `修改 ~` org/apache/commons/lang3/time/TimeZones.class
- `修改 ~` org/apache/commons/lang3/tuple/ImmutablePair.class
- `修改 ~` org/apache/commons/lang3/tuple/ImmutableTriple.class
- `修改 ~` org/apache/commons/lang3/tuple/MutablePair.class
- `修改 ~` org/apache/commons/lang3/tuple/MutableTriple.class
- `修改 ~` org/apache/commons/lang3/tuple/Pair.class
- `修改 ~` org/apache/commons/lang3/tuple/Triple.class
- `新增 +` META-INF/versions/9/module-info.class
- `新增 +` org/apache/commons/lang3/ArrayFill.class
- `新增 +` org/apache/commons/lang3/DoubleRange.class
- `新增 +` org/apache/commons/lang3/IntegerRange.class
- `新增 +` org/apache/commons/lang3/LongRange.class
- `新增 +` org/apache/commons/lang3/NumberRange.class
- `新增 +` org/apache/commons/lang3/SystemProperties.class
- `新增 +` org/apache/commons/lang3/arch/package-info.class
- `新增 +` org/apache/commons/lang3/builder/AbstractSupplier.class
- `新增 +` org/apache/commons/lang3/builder/DiffExclude.class
- `新增 +` org/apache/commons/lang3/builder/Reflection.class
- `新增 +` org/apache/commons/lang3/builder/package-info.class
- `新增 +` org/apache/commons/lang3/compare/package-info.class
- `新增 +` org/apache/commons/lang3/concurrent/AbstractConcurrentInitializer$AbstractBuilder.class
- `新增 +` org/apache/commons/lang3/concurrent/AbstractConcurrentInitializer.class
- `新增 +` org/apache/commons/lang3/concurrent/AbstractFutureProxy.class
- `新增 +` org/apache/commons/lang3/concurrent/AtomicInitializer$1.class
- `新增 +` org/apache/commons/lang3/concurrent/AtomicInitializer$Builder.class
- `新增 +` org/apache/commons/lang3/concurrent/AtomicSafeInitializer$1.class
- `新增 +` org/apache/commons/lang3/concurrent/AtomicSafeInitializer$Builder.class
- `新增 +` org/apache/commons/lang3/concurrent/BackgroundInitializer$1.class
- `新增 +` org/apache/commons/lang3/concurrent/BackgroundInitializer$Builder.class
- `新增 +` org/apache/commons/lang3/concurrent/FutureTasks.class
- `新增 +` org/apache/commons/lang3/concurrent/LazyInitializer$1.class
- `新增 +` org/apache/commons/lang3/concurrent/LazyInitializer$Builder.class
- `新增 +` org/apache/commons/lang3/concurrent/UncheckedExecutionException.class
- `新增 +` org/apache/commons/lang3/concurrent/UncheckedFuture.class
- `新增 +` org/apache/commons/lang3/concurrent/UncheckedFutureImpl.class
- `新增 +` org/apache/commons/lang3/concurrent/UncheckedTimeoutException.class
- `新增 +` org/apache/commons/lang3/concurrent/locks/package-info.class
- `新增 +` org/apache/commons/lang3/concurrent/package-info.class
- `新增 +` org/apache/commons/lang3/event/package-info.class
- `新增 +` org/apache/commons/lang3/exception/UncheckedException.class
- `新增 +` org/apache/commons/lang3/exception/UncheckedIllegalAccessException.class
- `新增 +` org/apache/commons/lang3/exception/UncheckedInterruptedException.class
- `新增 +` org/apache/commons/lang3/exception/UncheckedReflectiveOperationException.class
- `新增 +` org/apache/commons/lang3/exception/package-info.class
- `新增 +` org/apache/commons/lang3/function/BooleanConsumer.class
- `新增 +` org/apache/commons/lang3/function/Consumers.class
- `新增 +` org/apache/commons/lang3/function/Functions.class
- `新增 +` org/apache/commons/lang3/function/IntToCharFunction.class
- `新增 +` org/apache/commons/lang3/function/MethodInvokers.class
- `新增 +` org/apache/commons/lang3/function/Suppliers.class
- `新增 +` org/apache/commons/lang3/function/TriConsumer.class
- `新增 +` org/apache/commons/lang3/function/package-info.class
- `新增 +` org/apache/commons/lang3/math/package-info.class
- `新增 +` org/apache/commons/lang3/mutable/package-info.class
- `新增 +` org/apache/commons/lang3/package-info.class
- `新增 +` org/apache/commons/lang3/reflect/package-info.class
- `新增 +` org/apache/commons/lang3/stream/IntStreams.class
- `新增 +` org/apache/commons/lang3/stream/LangCollectors$1.class
- `新增 +` org/apache/commons/lang3/stream/LangCollectors$SimpleCollector.class
- `新增 +` org/apache/commons/lang3/stream/LangCollectors.class
- `新增 +` org/apache/commons/lang3/stream/Streams$EnumerationSpliterator.class
- `新增 +` org/apache/commons/lang3/stream/package-info.class
- `新增 +` org/apache/commons/lang3/text/package-info.class
- `新增 +` org/apache/commons/lang3/text/translate/package-info.class
- `新增 +` org/apache/commons/lang3/time/AbstractFormatCache$ArrayKey.class
- `新增 +` org/apache/commons/lang3/time/AbstractFormatCache.class
- `新增 +` org/apache/commons/lang3/time/package-info.class
- `新增 +` org/apache/commons/lang3/tuple/package-info.class
- `新增 +` org/apache/commons/lang3/util/FluentBitSet.class
- `新增 +` org/apache/commons/lang3/util/package-info.class
- `删除 -` org/apache/commons/lang3/time/FormatCache$ArrayKey.class
- `删除 -` org/apache/commons/lang3/time/FormatCache.class
- `删除 -` org/apache/commons/lang3/tuple/Pair$PairAdapter.class
- `删除 -` org/apache/commons/lang3/tuple/Triple$TripleAdapter.class

## 三、反编译源码级差异（Top-15 修改/新增/删除类）
> 共 15 个类已反编译展示（其余受 Top-K 限制未展开）。

### org/apache/commons/lang3/AnnotationUtils$1.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.AnnotationUtils
   *  org.apache.commons.lang3.ClassUtils
   *  org.apache.commons.lang3.builder.ToStringStyle
   */
  package org.apache.commons.lang3;
  
  import java.lang.annotation.Annotation;
  import org.apache.commons.lang3.AnnotationUtils;
  import org.apache.commons.lang3.ClassUtils;
  import org.apache.commons.lang3.builder.ToStringStyle;
  
  /*
   * Exception performing whole class analysis ignored.
   */
- static final class AnnotationUtils.1
+ class AnnotationUtils.1
  extends ToStringStyle {
      private static final long serialVersionUID = 1L;
  
      AnnotationUtils.1() {
          this.setDefaultFullDetail(true);
          this.setArrayContentDetail(true);
          this.setUseClassName(true);
          this.setUseShortClassName(true);
          this.setUseIdentityHashCode(false);
          this.setContentStart("(");
          this.setContentEnd(")");
          this.setFieldSeparator(", ");
          this.setArrayStart("[");
          this.setArrayEnd("]");
      }
  
-     protected String getShortClassName(Class<?> cls) {
-         for (Class iface : ClassUtils.getAllInterfaces(cls)) {
-             if (!Annotation.class.isAssignableFrom(iface)) continue;
-             return "@" + iface.getName();
-         }
-         return "";
-     }
- 
      protected void appendDetail(StringBuffer buffer, String fieldName, Object value) {
          if (value instanceof Annotation) {
              value = AnnotationUtils.toString((Annotation)((Annotation)value));
          }
          super.appendDetail(buffer, fieldName, value);
      }
+ 
+     protected String getShortClassName(Class<?> cls) {
+         return ClassUtils.getAllInterfaces(cls).stream().filter(Annotation.class::isAssignableFrom).findFirst().map(iface -> "@" + iface.getName()).orElse("");
+     }
  }
  

```

### org/apache/commons/lang3/AnnotationUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
+  *  org.apache.commons.lang3.ObjectUtils
   *  org.apache.commons.lang3.Validate
   *  org.apache.commons.lang3.builder.ToStringBuilder
   *  org.apache.commons.lang3.builder.ToStringStyle
+  *  org.apache.commons.lang3.exception.UncheckedException
   */
  package org.apache.commons.lang3;
  
  import java.lang.annotation.Annotation;
- import java.lang.reflect.InvocationTargetException;
  import java.lang.reflect.Method;
  import java.util.Arrays;
+ import org.apache.commons.lang3.ObjectUtils;
  import org.apache.commons.lang3.Validate;
  import org.apache.commons.lang3.builder.ToStringBuilder;
  import org.apache.commons.lang3.builder.ToStringStyle;
+ import org.apache.commons.lang3.exception.UncheckedException;
  
  public class AnnotationUtils {
      private static final ToStringStyle TO_STRING_STYLE = new /* Unavailable Anonymous Inner Class!! */;
  
+     private static boolean annotationArrayMemberEquals(Annotation[] a1, Annotation[] a2) {
+         if (a1.length != a2.length) {
+             return false;
+         }
+         for (int i = 0; i < a1.length; ++i) {
+             if (AnnotationUtils.equals(a1[i], a2[i])) continue;
+             return false;
+         }
+         return true;
+     }
+ 
+     private static boolean arrayMemberEquals(Class<?> componentType, Object o1, Object o2) {
+         if (componentType.isAnnotation()) {
+             return AnnotationUtils.annotationArrayMemberEquals((Annotation[])o1, (Annotation[])o2);
+         }
+         if (componentType.equals(Byte.TYPE)) {
+             return Arrays.equals((byte[])o1, (byte[])o2);
+         }
+         if (componentType.equals(Short.TYPE)) {
+             return Arrays.equals((short[])o1, (short[])o2);
+         }
+         if (componentType.equals(Integer.TYPE)) {
+             return Arrays.equals((int[])o1, (int[])o2);
+         }
+         if (componentType.equals(Character.TYPE)) {
+             return Arrays.equals((char[])o1, (char[])o2);
+         }
+         if (componentType.equals(Long.TYPE)) {
+             return Arrays.equals((long[])o1, (long[])o2);
+         }
+         if (componentType.equals(Float.TYPE)) {
+             return Arrays.equals((float[])o1, (float[])o2);
+         }
+         if (componentType.equals(Double.TYPE)) {
+             return Arrays.equals((double[])o1, (double[])o2);
+         }
+         if (componentType.equals(Boolean.TYPE)) {
+             return Arrays.equals((boolean[])o1, (boolean[])o2);
+         }
+         return Arrays.equals((Object[])o1, (Object[])o2);
+     }
+ 
+     private static int arrayMemberHash(Class<?> componentType, Object o) {
+         if (componentType.equals(Byte.TYPE)) {
+             return Arrays.hashCode((byte[])o);
+         }
+         if (componentType.equals(Short.TYPE)) {
+             return Arrays.hashCode((short[])o);
+         }
+         if (componentType.equals(Integer.TYPE)) {
+             return Arrays.hashCode((int[])o);
+         }
+         if (componentType.equals(Character.TYPE)) {
+             return Arrays.hashCode((char[])o);
+         }
+         if (componentType.equals(Long.TYPE)) {
+             return Arrays.hashCode((long[])o);
+         }
+         if (componentType.equals(Float.TYPE)) {
+             return Arrays.hashCode((float[])o);
+         }
+         if (componentType.equals(Double.TYPE)) {
+             return Arrays.hashCode((double[])o);
+         }
+         if (componentType.equals(Boolean.TYPE)) {
+             return Arrays.hashCode((boolean[])o);
+         }
+         return Arrays.hashCode((Object[])o);
+     }
+ 
      public static boolean equals(Annotation a1, Annotation a2) {
          if (a1 == a2) {
              return true;
          }
          if (a1 == null || a2 == null) {
              return false;
          }
          Class<? extends Annotation> type1 = a1.annotationType();
          Class<? extends Annotation> type2 = a2.annotationType();
          Validate.notNull(type1, (String)"Annotation %s with null annotationType()", (Object[])new Object[]{a1});
          Validate.notNull(type2, (String)"Annotation %s with null annotationType()", (Object[])new Object[]{a2});
          if (!type1.equals(type2)) {
              return false;
          }
          try {
              for (Method m : type1.getDeclaredMethods()) {
                  if (m.getParameterTypes().length != 0 || !AnnotationUtils.isValidAnnotationMemberType(m.getReturnType())) continue;
                  Object v1 = m.invoke(a1, new Object[0]);
                  Object v2 = m.invoke(a2, new Object[0]);
                  if (AnnotationUtils.memberEquals(m.getReturnType(), v1, v2)) continue;
                  return false;
              }
          }
-         catch (IllegalAccessException | InvocationTargetException ex) {
+         catch (ReflectiveOperationException ex) {
              return false;
          }
          return true;
      }
  
      public static int hashCode(Annotation a) {
          int result = 0;
          Class<? extends Annotation> type = a.annotationType();
          for (Method m : type.getDeclaredMethods()) {
              try {
                  Object value = m.invoke(a, new Object[0]);
                  if (value == null) {
                      throw new IllegalStateException(String.format("Annotation method %s returned null", m));
                  }
                  result += AnnotationUtils.hashMember(m.getName(), value);
              }
-             catch (RuntimeException ex) {
-                 throw ex;
-             }
-             catch (Exception ex) {
-                 throw new RuntimeException(ex);
+             catch (ReflectiveOperationException ex) {
+                 throw new UncheckedException((Throwable)ex);
              }
          }
          return result;
      }
  
-     public static String toString(Annotation a) {
-         ToStringBuilder builder = new ToStringBuilder((Object)a, TO_STRING_STYLE);
-         for (Method m : a.annotationType().getDeclaredMethods()) {
-             if (m.getParameterTypes().length > 0) continue;
-             try {
-                 builder.append(m.getName(), m.invoke(a, new Object[0]));
-             }
-             catch (RuntimeException ex) {
-                 throw ex;
-             }
-             catch (Exception ex) {
-                 throw new RuntimeException(ex);
-             }
+     private static int hashMember(String name, Object value) {
+         int part1 = name.hashCode() * 127;
+         if (ObjectUtils.isArray((Object)value)) {
+             return part1 ^ AnnotationUtils.arrayMemberHash(value.getClass().getComponentType(), value);
          }
-         return builder.build();
+         if (value instanceof Annotation) {
+             return part1 ^ AnnotationUtils.hashCode((Annotation)value);
+         }
+         return part1 ^ value.hashCode();
      }
  
      public static boolean isValidAnnotationMemberType(Class<?> type) {
          if (type == null) {
              return false;
          }
          if (type.isArray()) {
              type = type.getComponentType();
          }
          return type.isPrimitive() || type.isEnum() || type.isAnnotation() || String.class.equals(type) || Class.class.equals(type);
      }
  
-     private static int hashMember(String name, Object value) {
-         int part1 = name.hashCode() * 127;
-         if (value.getClass().isArray()) {
-             return part1 ^ AnnotationUtils.arrayMemberHash(value.getClass().getComponentType(), value);
-         }
-         if (value instanceof Annotation) {
-             return part1 ^ AnnotationUtils.hashCode((Annotation)value);
-         }
-         return part1 ^ value.hashCode();
-     }
- 
      private static boolean memberEquals(Class<?> type, Object o1, Object o2) {
          if (o1 == o2) {
              return true;
          }
          if (o1 == null || o2 == null) {
              return false;
          }
          if (type.isArray()) {
              return AnnotationUtils.arrayMemberEquals(type.getComponentType(), o1, o2);
          }
          if (type.isAnnotation()) {
              return AnnotationUtils.equals((Annotation)o1, (Annotation)o2);
          }
          return o1.equals(o2);
      }
  
-     private static boolean arrayMemberEquals(Class<?> componentType, Object o1, Object o2) {
-         if (componentType.isAnnotation()) {
-             return AnnotationUtils.annotationArrayMemberEquals((Annotation[])o1, (Annotation[])o2);
-         }
-         if (componentType.equals(Byte.TYPE)) {
-             return Arrays.equals((byte[])o1, (byte[])o2);
-         }
-         if (componentType.equals(Short.TYPE)) {
-             return Arrays.equals((short[])o1, (short[])o2);
-         }
-         if (componentType.equals(Integer.TYPE)) {
-             return Arrays.equals((int[])o1, (int[])o2);
-         }
-         if (componentType.equals(Character.TYPE)) {
-             return Arrays.equals((char[])o1, (char[])o2);
-         }
-         if (componentType.equals(Long.TYPE)) {
-             return Arrays.equals((long[])o1, (long[])o2);
-         }
-         if (componentType.equals(Float.TYPE)) {
-             return Arrays.equals((float[])o1, (float[])o2);
-         }
-         if (componentType.equals(Double.TYPE)) {
-             return Arrays.equals((double[])o1, (double[])o2);
-         }
-         if (componentType.equals(Boolean.TYPE)) {
-             return Arrays.equals((boolean[])o1, (boolean[])o2);
-         }
-         return Arrays.equals((Object[])o1, (Object[])o2);
-     }
- 
-     private static boolean annotationArrayMemberEquals(Annotation[] a1, Annotation[] a2) {
-         if (a1.length != a2.length) {
-             return false;
-         }
-         for (int i = 0; i < a1.length; ++i) {
-             if (AnnotationUtils.equals(a1[i], a2[i])) continue;
-             return false;
-         }
-         return true;
-     }
- 
-     private static int arrayMemberHash(Class<?> componentType, Object o) {
-         if (componentType.equals(Byte.TYPE)) {
-             return Arrays.hashCode((byte[])o);
-         }
-         if (componentType.equals(Short.TYPE)) {
-             return Arrays.hashCode((short[])o);
-         }
-         if (componentType.equals(Integer.TYPE)) {
-             return Arrays.hashCode((int[])o);
-         }
-         if (componentType.equals(Character.TYPE)) {
-             return Arrays.hashCode((char[])o);
-         }
-         if (componentType.equals(Long.TYPE)) {
-             return Arrays.hashCode((long[])o);
-         }
-         if (componentType.equals(Float.TYPE)) {
-             return Arrays.hashCode((float[])o);
-         }
-         if (componentType.equals(Double.TYPE)) {
-             return Arrays.hashCode((double[])o);
-         }
-         if (componentType.equals(Boolean.TYPE)) {
-             return Arrays.hashCode((boolean[])o);
+     public static String toString(Annotation a) {
+         ToStringBuilder builder = new ToStringBuilder((Object)a, TO_STRING_STYLE);
+         for (Method m : a.annotationType().getDeclaredMethods()) {
+             if (m.getParameterTypes().length > 0) continue;
+             try {
+                 builder.append(m.getName(), m.invoke(a, new Object[0]));
+             }
+             catch (ReflectiveOperationException ex) {
+                 throw new UncheckedException((Throwable)ex);
+             }
          }
-         return Arrays.hashCode((Object[])o);
+         return builder.build();
      }
  }
  

```

### org/apache/commons/lang3/ArchUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
-  *  org.apache.commons.lang3.SystemUtils
+  *  org.apache.commons.lang3.SystemProperties
   *  org.apache.commons.lang3.arch.Processor
   *  org.apache.commons.lang3.arch.Processor$Arch
   *  org.apache.commons.lang3.arch.Processor$Type
+  *  org.apache.commons.lang3.stream.Streams
   */
  package org.apache.commons.lang3;
  
  import java.util.HashMap;
  import java.util.Map;
- import java.util.stream.Stream;
- import org.apache.commons.lang3.SystemUtils;
+ import org.apache.commons.lang3.SystemProperties;
  import org.apache.commons.lang3.arch.Processor;
+ import org.apache.commons.lang3.stream.Streams;
  
  public class ArchUtils {
      private static final Map<String, Processor> ARCH_TO_PROCESSOR = new HashMap<String, Processor>();
  
+     private static void addProcessor(String key, Processor processor) {
+         if (ARCH_TO_PROCESSOR.containsKey(key)) {
+             throw new IllegalStateException("Key " + key + " already exists in processor map");
+         }
+         ARCH_TO_PROCESSOR.put(key, processor);
+     }
+ 
+     private static void addProcessors(Processor processor, String ... keys) {
+         Streams.of((Object[])keys).forEach(e -> ArchUtils.addProcessor(e, processor));
+     }
+ 
+     public static Processor getProcessor() {
+         return ArchUtils.getProcessor(SystemProperties.getOsArch());
+     }
+ 
+     public static Processor getProcessor(String value) {
+         return ARCH_TO_PROCESSOR.get(value);
+     }
+ 
      private static void init() {
          ArchUtils.init_X86_32Bit();
          ArchUtils.init_X86_64Bit();
          ArchUtils.init_IA64_32Bit();
          ArchUtils.init_IA64_64Bit();
          ArchUtils.init_PPC_32Bit();
          ArchUtils.init_PPC_64Bit();
-     }
- 
-     private static void init_X86_32Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_32, Processor.Type.X86);
-         ArchUtils.addProcessors(processor, "x86", "i386", "i486", "i586", "i686", "pentium");
+         ArchUtils.init_Aarch_64Bit();
+         ArchUtils.init_RISCV_32Bit();
+         ArchUtils.init_RISCV_64Bit();
      }
  
-     private static void init_X86_64Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_64, Processor.Type.X86);
-         ArchUtils.addProcessors(processor, "x86_64", "amd64", "em64t", "universal");
+     private static void init_Aarch_64Bit() {
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_64, Processor.Type.AARCH_64), "aarch64");
      }
  
      private static void init_IA64_32Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_32, Processor.Type.IA_64);
-         ArchUtils.addProcessors(processor, "ia64_32", "ia64n");
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_32, Processor.Type.IA_64), "ia64_32", "ia64n");
      }
  
      private static void init_IA64_64Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_64, Processor.Type.IA_64);
-         ArchUtils.addProcessors(processor, "ia64", "ia64w");
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_64, Processor.Type.IA_64), "ia64", "ia64w");
      }
  
      private static void init_PPC_32Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_32, Processor.Type.PPC);
-         ArchUtils.addProcessors(processor, "ppc", "power", "powerpc", "power_pc", "power_rs");
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_32, Processor.Type.PPC), "ppc", "power", "powerpc", "power_pc", "power_rs");
      }
  
      private static void init_PPC_64Bit() {
-         Processor processor = new Processor(Processor.Arch.BIT_64, Processor.Type.PPC);
-         ArchUtils.addProcessors(processor, "ppc64", "power64", "powerpc64", "power_pc64", "power_rs64");
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_64, Processor.Type.PPC), "ppc64", "power64", "powerpc64", "power_pc64", "power_rs64");
      }
  
-     private static void addProcessor(String key, Processor processor) {
-         if (ARCH_TO_PROCESSOR.containsKey(key)) {
-             throw new IllegalStateException("Key " + key + " already exists in processor map");
-         }
-         ARCH_TO_PROCESSOR.put(key, processor);
+     private static void init_RISCV_32Bit() {
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_32, Processor.Type.RISC_V), "riscv32");
      }
  
-     private static void addProcessors(Processor processor, String ... keys) {
-         Stream.of(keys).forEach(e -> ArchUtils.addProcessor(e, processor));
+     private static void init_RISCV_64Bit() {
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_64, Processor.Type.RISC_V), "riscv64");
      }
  
-     public static Processor getProcessor() {
-         return ArchUtils.getProcessor(SystemUtils.OS_ARCH);
+     private static void init_X86_32Bit() {
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_32, Processor.Type.X86), "x86", "i386", "i486", "i586", "i686", "pentium");
      }
  
-     public static Processor getProcessor(String value) {
-         return ARCH_TO_PROCESSOR.get(value);
+     private static void init_X86_64Bit() {
+         ArchUtils.addProcessors(new Processor(Processor.Arch.BIT_64, Processor.Type.X86), "x86_64", "amd64", "em64t", "universal");
      }
  
      static {
          ArchUtils.init();
      }
  }
  

```

### org/apache/commons/lang3/ArraySorter.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.apache.commons.lang3;
  
  import java.util.Arrays;
  import java.util.Comparator;
  
  public class ArraySorter {
      public static byte[] sort(byte[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static char[] sort(char[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static double[] sort(double[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static float[] sort(float[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static int[] sort(int[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static long[] sort(long[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static short[] sort(short[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static <T> T[] sort(T[] array) {
          Arrays.sort(array);
          return array;
      }
  
      public static <T> T[] sort(T[] array, Comparator<? super T> comparator) {
          Arrays.sort(array, comparator);
          return array;
      }
  }
  

```

### org/apache/commons/lang3/ArrayUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.ArraySorter
   *  org.apache.commons.lang3.BooleanUtils
   *  org.apache.commons.lang3.CharUtils
   *  org.apache.commons.lang3.ClassUtils
+  *  org.apache.commons.lang3.ObjectUtils
   *  org.apache.commons.lang3.builder.EqualsBuilder
   *  org.apache.commons.lang3.builder.HashCodeBuilder
   *  org.apache.commons.lang3.builder.ToStringBuilder
   *  org.apache.commons.lang3.builder.ToStringStyle
   *  org.apache.commons.lang3.math.NumberUtils
   *  org.apache.commons.lang3.mutable.MutableInt
+  *  org.apache.commons.lang3.stream.Streams
   */
  package org.apache.commons.lang3;
  
  import java.lang.reflect.Array;
  import java.lang.reflect.Field;
  import java.lang.reflect.Method;
  import java.lang.reflect.Type;
+ import java.util.Arrays;
  import java.util.BitSet;
  import java.util.Comparator;
  import java.util.HashMap;
  import java.util.Map;
+ import java.util.Objects;
  import java.util.Random;
+ import java.util.concurrent.ThreadLocalRandom;
+ import java.util.function.IntFunction;
+ import java.util.function.Supplier;
  import org.apache.commons.lang3.ArraySorter;
  import org.apache.commons.lang3.BooleanUtils;
  import org.apache.commons.lang3.CharUtils;
  import org.apache.commons.lang3.ClassUtils;
+ import org.apache.commons.lang3.ObjectUtils;
  import org.apache.commons.lang3.builder.EqualsBuilder;
  import org.apache.commons.lang3.builder.HashCodeBuilder;
  import org.apache.commons.lang3.builder.ToStringBuilder;
  import org.apache.commons.lang3.builder.ToStringStyle;
  import org.apache.commons.lang3.math.NumberUtils;
  import org.apache.commons.lang3.mutable.MutableInt;
+ import org.apache.commons.lang3.stream.Streams;
  
  public class ArrayUtils {
      public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
      public static final Boolean[] EMPTY_BOOLEAN_OBJECT_ARRAY = new Boolean[0];
      public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
      public static final Byte[] EMPTY_BYTE_OBJECT_ARRAY = new Byte[0];
      public static final char[] EMPTY_CHAR_ARRAY = new char[0];
      public static final Character[] EMPTY_CHARACTER_OBJECT_ARRAY = new Character[0];
      public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
      public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];
      public static final Double[] EMPTY_DOUBLE_OBJECT_ARRAY = new Double[0];
      public static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
      public static final float[] EMPTY_FLOAT_ARRAY = new float[0];
      public static final Float[] EMPTY_FLOAT_OBJECT_ARRAY = new Float[0];
      public static final int[] EMPTY_INT_ARRAY = new int[0];
      public static final Integer[] EMPTY_INTEGER_OBJECT_ARRAY = new Integer[0];
      public static final long[] EMPTY_LONG_ARRAY = new long[0];
      public static final Long[] EMPTY_LONG_OBJECT_ARRAY = new Long[0];
      public static final Method[] EMPTY_METHOD_ARRAY = new Method[0];
      public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
      public static final short[] EMPTY_SHORT_ARRAY = new short[0];
      public static final Short[] EMPTY_SHORT_OBJECT_ARRAY = new Short[0];
      public static final String[] EMPTY_STRING_ARRAY = new String[0];
      public static final Throwable[] EMPTY_THROWABLE_ARRAY = new Throwable[0];
      public static final Type[] EMPTY_TYPE_ARRAY = new Type[0];
      public static final int INDEX_NOT_FOUND = -1;
  
      public static boolean[] add(boolean[] array, boolean element) {
          boolean[] newArray = (boolean[])ArrayUtils.copyArrayGrow1(array, Boolean.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static boolean[] add(boolean[] array, int index, boolean element) {
          return (boolean[])ArrayUtils.add(array, index, element, Boolean.TYPE);
      }
  
      public static byte[] add(byte[] array, byte element) {
          byte[] newArray = (byte[])ArrayUtils.copyArrayGrow1(array, Byte.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static byte[] add(byte[] array, int index, byte element) {
          return (byte[])ArrayUtils.add(array, index, element, Byte.TYPE);
      }
  
      public static char[] add(char[] array, char element) {
          char[] newArray = (char[])ArrayUtils.copyArrayGrow1(array, Character.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static char[] add(char[] array, int index, char element) {
          return (char[])ArrayUtils.add(array, index, Character.valueOf(element), Character.TYPE);
      }
  
      public static double[] add(double[] array, double element) {
          double[] newArray = (double[])ArrayUtils.copyArrayGrow1(array, Double.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static double[] add(double[] array, int index, double element) {
          return (double[])ArrayUtils.add(array, index, element, Double.TYPE);
      }
  
      public static float[] add(float[] array, float element) {
          float[] newArray = (float[])ArrayUtils.copyArrayGrow1(array, Float.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static float[] add(float[] array, int index, float element) {
          return (float[])ArrayUtils.add(array, index, Float.valueOf(element), Float.TYPE);
      }
  
      public static int[] add(int[] array, int element) {
          int[] newArray = (int[])ArrayUtils.copyArrayGrow1(array, Integer.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static int[] add(int[] array, int index, int element) {
          return (int[])ArrayUtils.add(array, index, element, Integer.TYPE);
      }
  
      @Deprecated
      public static long[] add(long[] array, int index, long element) {
          return (long[])ArrayUtils.add(array, index, element, Long.TYPE);
      }
  
      public static long[] add(long[] array, long element) {
          long[] newArray = (long[])ArrayUtils.copyArrayGrow1(array, Long.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
-     private static Object add(Object array, int index, Object element, Class<?> clss) {
+     private static Object add(Object array, int index, Object element, Class<?> clazz) {
          if (array == null) {
              if (index != 0) {
                  throw new IndexOutOfBoundsException("Index: " + index + ", Length: 0");
              }
-             Object joinedArray = Array.newInstance(clss, 1);
+             Object joinedArray = Array.newInstance(clazz, 1);
              Array.set(joinedArray, 0, element);
              return joinedArray;
          }
          int length = Array.getLength(array);
          if (index > length || index < 0) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
          }
-         Object result = Array.newInstance(clss, length + 1);
+         Object result = Array.newInstance(clazz, length + 1);
          System.arraycopy(array, 0, result, 0, index);
          Array.set(result, index, element);
          if (index < length) {
              System.arraycopy(array, index, result, index + 1, length - index);
          }
          return result;
      }
  
      @Deprecated
      public static short[] add(short[] array, int index, short element) {
          return (short[])ArrayUtils.add(array, index, element, Short.TYPE);
      }
  
      public static short[] add(short[] array, short element) {
          short[] newArray = (short[])ArrayUtils.copyArrayGrow1(array, Short.TYPE);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      @Deprecated
      public static <T> T[] add(T[] array, int index, T element) {
-         Class<?> clss = null;
+         Class clazz;
          if (array != null) {
-             clss = array.getClass().getComponentType();
+             clazz = ArrayUtils.getComponentType(array);
          } else if (element != null) {
-             clss = element.getClass();
+             clazz = ObjectUtils.getClass(element);
          } else {
              throw new IllegalArgumentException("Array and element cannot both be null");
          }
-         Object[] newArray = (Object[])ArrayUtils.add(array, index, element, clss);
-         return newArray;
+         return (Object[])ArrayUtils.add(array, index, element, clazz);
      }
  
      public static <T> T[] add(T[] array, T element) {
          Class<?> type;
          if (array != null) {
              type = array.getClass().getComponentType();
          } else if (element != null) {
              type = element.getClass();
          } else {
              throw new IllegalArgumentException("Arguments cannot both be null");
          }
          Object[] newArray = (Object[])ArrayUtils.copyArrayGrow1(array, type);
          newArray[newArray.length - 1] = element;
          return newArray;
      }
  
      public static boolean[] addAll(boolean[] array1, boolean ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          boolean[] joinedArray = new boolean[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static byte[] addAll(byte[] array1, byte ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          byte[] joinedArray = new byte[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static char[] addAll(char[] array1, char ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          char[] joinedArray = new char[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static double[] addAll(double[] array1, double ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          double[] joinedArray = new double[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static float[] addAll(float[] array1, float ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          float[] joinedArray = new float[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static int[] addAll(int[] array1, int ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          int[] joinedArray = new int[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static long[] addAll(long[] array1, long ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          long[] joinedArray = new long[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static short[] addAll(short[] array1, short ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
          short[] joinedArray = new short[array1.length + array2.length];
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          return joinedArray;
      }
  
      public static <T> T[] addAll(T[] array1, T ... array2) {
          if (array1 == null) {
              return ArrayUtils.clone(array2);
          }
          if (array2 == null) {
              return ArrayUtils.clone(array1);
          }
-         Class<?> type1 = array1.getClass().getComponentType();
-         Object[] joinedArray = (Object[])Array.newInstance(type1, array1.length + array2.length);
+         Class<T> type1 = ArrayUtils.getComponentType(array1);
+         T[] joinedArray = ArrayUtils.newInstance(type1, array1.length + array2.length);
          System.arraycopy(array1, 0, joinedArray, 0, array1.length);
          try {
              System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
          }
          catch (ArrayStoreException ase) {
              Class<?> type2 = array2.getClass().getComponentType();
              if (!type1.isAssignableFrom(type2)) {
                  throw new IllegalArgumentException("Cannot store " + type2.getName() + " in an array of " + type1.getName(), ase);
              }
              throw ase;
          }
          return joinedArray;
      }
  
      public static boolean[] addFirst(boolean[] array, boolean element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static byte[] addFirst(byte[] array, byte element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static char[] addFirst(char[] array, char element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static double[] addFirst(double[] array, double element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static float[] addFirst(float[] array, float element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static int[] addFirst(int[] array, int element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static long[] addFirst(long[] array, long element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static short[] addFirst(short[] array, short element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static <T> T[] addFirst(T[] array, T element) {
          return array == null ? ArrayUtils.add(array, element) : ArrayUtils.insert(0, array, element);
      }
  
      public static boolean[] clone(boolean[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (boolean[])array.clone();
+         return array != null ? (boolean[])array.clone() : null;
      }
  
      public static byte[] clone(byte[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (byte[])array.clone();
+         return array != null ? (byte[])array.clone() : null;
      }
  
      public static char[] clone(char[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (char[])array.clone();
+         return array != null ? (char[])array.clone() : null;
      }
  
      public static double[] clone(double[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (double[])array.clone();
+         return array != null ? (double[])array.clone() : null;
      }
  
      public static float[] clone(float[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (float[])array.clone();
+         return array != null ? (float[])array.clone() : null;
      }
  
      public static int[] clone(int[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (int[])array.clone();
+         return array != null ? (int[])array.clone() : null;
      }
  
      public static long[] clone(long[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (long[])array.clone();
+         return array != null ? (long[])array.clone() : null;
      }
  
      public static short[] clone(short[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (short[])array.clone();
+         return array != null ? (short[])array.clone() : null;
      }
  
      public static <T> T[] clone(T[] array) {
-         if (array == null) {
-             return null;
-         }
-         return (Object[])array.clone();
+         return array != null ? (Object[])array.clone() : null;
      }
  
      public static boolean contains(boolean[] array, boolean valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(byte[] array, byte valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(char[] array, char valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(double[] array, double valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(double[] array, double valueToFind, double tolerance) {
          return ArrayUtils.indexOf(array, valueToFind, 0, tolerance) != -1;
      }
  
      public static boolean contains(float[] array, float valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(int[] array, int valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(long[] array, long valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
      public static boolean contains(Object[] array, Object objectToFind) {
          return ArrayUtils.indexOf(array, objectToFind) != -1;
      }
  
      public static boolean contains(short[] array, short valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind) != -1;
      }
  
+     public static boolean containsAny(Object[] array, Object ... objectsToFind) {
+         return Streams.of((Object[])objectsToFind).anyMatch(e -> ArrayUtils.contains(array, e));
+     }
+ 
      private static Object copyArrayGrow1(Object array, Class<?> newArrayComponentType) {
          if (array != null) {
              int arrayLength = Array.getLength(array);
              Object newArray = Array.newInstance(array.getClass().getComponentType(), arrayLength + 1);
              System.arraycopy(array, 0, newArray, 0, arrayLength);
              return newArray;
          }
          return Array.newInstance(newArrayComponentType, 1);
      }
  
      public static <T> T get(T[] array, int index) {
          return ArrayUtils.get(array, index, null);
      }
  
      public static <T> T get(T[] array, int index, T defaultValue) {
          return ArrayUtils.isArrayIndexValid(array, index) ? array[index] : defaultValue;
      }
  
+     public static <T> Class<T> getComponentType(T[] array) {
+         return ClassUtils.getComponentType((Class)ObjectUtils.getClass(array));
+     }
+ 
      public static int getLength(Object array) {
-         if (array == null) {
-             return 0;
-         }
-         return Array.getLength(array);
+         return array != null ? Array.getLength(array) : 0;
      }
  
      public static int hashCode(Object array) {
          return new HashCodeBuilder().append(array).toHashCode();
      }
  
      public static BitSet indexesOf(boolean[] array, boolean valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(boolean[] array, boolean valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(byte[] array, byte valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(byte[] array, byte valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(char[] array, char valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(char[] array, char valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(double[] array, double valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(double[] array, double valueToFind, double tolerance) {
          return ArrayUtils.indexesOf(array, valueToFind, 0, tolerance);
      }
  
      public static BitSet indexesOf(double[] array, double valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(double[] array, double valueToFind, int startIndex, double tolerance) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex, tolerance)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(float[] array, float valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(float[] array, float valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(int[] array, int valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(int[] array, int valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(long[] array, long valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(long[] array, long valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(Object[] array, Object objectToFind) {
          return ArrayUtils.indexesOf(array, objectToFind, 0);
      }
  
      public static BitSet indexesOf(Object[] array, Object objectToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, objectToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static BitSet indexesOf(short[] array, short valueToFind) {
          return ArrayUtils.indexesOf(array, valueToFind, 0);
      }
  
      public static BitSet indexesOf(short[] array, short valueToFind, int startIndex) {
          BitSet bitSet = new BitSet();
          if (array == null) {
              return bitSet;
          }
          while (startIndex < array.length && (startIndex = ArrayUtils.indexOf(array, valueToFind, startIndex)) != -1) {
              bitSet.set(startIndex);
              ++startIndex;
          }
          return bitSet;
      }
  
      public static int indexOf(boolean[] array, boolean valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(boolean[] array, boolean valueToFind, int startIndex) {
          if (ArrayUtils.isEmpty(array)) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(byte[] array, byte valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(byte[] array, byte valueToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(char[] array, char valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(char[] array, char valueToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(double[] array, double valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(double[] array, double valueToFind, double tolerance) {
          return ArrayUtils.indexOf(array, valueToFind, 0, tolerance);
      }
  
      public static int indexOf(double[] array, double valueToFind, int startIndex) {
          if (ArrayUtils.isEmpty(array)) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          boolean searchNaN = Double.isNaN(valueToFind);
          for (int i = startIndex; i < array.length; ++i) {
              double element = array[i];
              if (valueToFind != element && (!searchNaN || !Double.isNaN(element))) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(double[] array, double valueToFind, int startIndex, double tolerance) {
          if (ArrayUtils.isEmpty(array)) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          double min = valueToFind - tolerance;
          double max = valueToFind + tolerance;
          for (int i = startIndex; i < array.length; ++i) {
              if (!(array[i] >= min) || !(array[i] <= max)) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(float[] array, float valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(float[] array, float valueToFind, int startIndex) {
          if (ArrayUtils.isEmpty(array)) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          boolean searchNaN = Float.isNaN(valueToFind);
          for (int i = startIndex; i < array.length; ++i) {
              float element = array[i];
              if (valueToFind != element && (!searchNaN || !Float.isNaN(element))) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(int[] array, int valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(int[] array, int valueToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(long[] array, long valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(long[] array, long valueToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int indexOf(Object[] array, Object objectToFind) {
          return ArrayUtils.indexOf(array, objectToFind, 0);
      }
  
      public static int indexOf(Object[] array, Object objectToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          if (objectToFind == null) {
              for (int i = startIndex; i < array.length; ++i) {
                  if (array[i] != null) continue;
                  return i;
              }
          } else {
              for (int i = startIndex; i < array.length; ++i) {
                  if (!objectToFind.equals(array[i])) continue;
                  return i;
              }
          }
          return -1;
      }
  
      public static int indexOf(short[] array, short valueToFind) {
          return ArrayUtils.indexOf(array, valueToFind, 0);
      }
  
      public static int indexOf(short[] array, short valueToFind, int startIndex) {
          if (array == null) {
              return -1;
          }
          if (startIndex < 0) {
              startIndex = 0;
          }
          for (int i = startIndex; i < array.length; ++i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static boolean[] insert(int index, boolean[] array, boolean ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          boolean[] result = new boolean[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static byte[] insert(int index, byte[] array, byte ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          byte[] result = new byte[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static char[] insert(int index, char[] array, char ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          char[] result = new char[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static double[] insert(int index, double[] array, double ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          double[] result = new double[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static float[] insert(int index, float[] array, float ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          float[] result = new float[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static int[] insert(int index, int[] array, int ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          int[] result = new int[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static long[] insert(int index, long[] array, long ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          long[] result = new long[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      public static short[] insert(int index, short[] array, short ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
          short[] result = new short[array.length + values.length];
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
      @SafeVarargs
      public static <T> T[] insert(int index, T[] array, T ... values) {
          if (array == null) {
              return null;
          }
          if (ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          if (index < 0 || index > array.length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + array.length);
          }
-         Class<?> type = array.getClass().getComponentType();
-         Object[] result = (Object[])Array.newInstance(type, array.length + values.length);
+         Class<T> type = ArrayUtils.getComponentType(array);
+         int length = array.length + values.length;
+         T[] result = ArrayUtils.newInstance(type, length);
          System.arraycopy(values, 0, result, index, values.length);
          if (index > 0) {
              System.arraycopy(array, 0, result, 0, index);
          }
          if (index < array.length) {
              System.arraycopy(array, index, result, index + values.length, array.length - index);
          }
          return result;
      }
  
+     private static boolean isArrayEmpty(Object array) {
+         return ArrayUtils.getLength(array) == 0;
+     }
+ 
      public static <T> boolean isArrayIndexValid(T[] array, int index) {
          return index >= 0 && ArrayUtils.getLength(array) > index;
      }
  
      public static boolean isEmpty(boolean[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(byte[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(char[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(double[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(float[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(int[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(long[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(Object[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      public static boolean isEmpty(short[] array) {
-         return ArrayUtils.getLength(array) == 0;
+         return ArrayUtils.isArrayEmpty(array);
      }
  
      @Deprecated
      public static boolean isEquals(Object array1, Object array2) {
          return new EqualsBuilder().append(array1, array2).isEquals();
      }
  
      public static boolean isNotEmpty(boolean[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(byte[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(char[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(double[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(float[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(int[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(long[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isNotEmpty(short[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static <T> boolean isNotEmpty(T[] array) {
          return !ArrayUtils.isEmpty(array);
      }
  
      public static boolean isSameLength(boolean[] array1, boolean[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(byte[] array1, byte[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(char[] array1, char[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(double[] array1, double[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(float[] array1, float[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(int[] array1, int[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(long[] array1, long[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(Object array1, Object array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(Object[] array1, Object[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameLength(short[] array1, short[] array2) {
          return ArrayUtils.getLength(array1) == ArrayUtils.getLength(array2);
      }
  
      public static boolean isSameType(Object array1, Object array2) {
          if (array1 == null || array2 == null) {
              throw new IllegalArgumentException("The Array must not be null");
          }
          return array1.getClass().getName().equals(array2.getClass().getName());
      }
  
      public static boolean isSorted(boolean[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          boolean previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              boolean current = array[i];
              if (BooleanUtils.compare((boolean)previous, (boolean)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(byte[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          byte previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              byte current = array[i];
              if (NumberUtils.compare((byte)previous, (byte)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(char[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          char previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              char current = array[i];
              if (CharUtils.compare((char)previous, (char)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(double[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          double previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              double current = array[i];
              if (Double.compare(previous, current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(float[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          float previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              float current = array[i];
              if (Float.compare(previous, current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(int[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          int previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              int current = array[i];
              if (NumberUtils.compare((int)previous, (int)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(long[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          long previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              long current = array[i];
              if (NumberUtils.compare((long)previous, (long)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static boolean isSorted(short[] array) {
-         if (array == null || array.length < 2) {
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          short previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              short current = array[i];
              if (NumberUtils.compare((short)previous, (short)current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static <T extends Comparable<? super T>> boolean isSorted(T[] array) {
          return ArrayUtils.isSorted(array, Comparable::compareTo);
      }
  
      public static <T> boolean isSorted(T[] array, Comparator<T> comparator) {
-         if (comparator == null) {
-             throw new IllegalArgumentException("Comparator should not be null.");
-         }
-         if (array == null || array.length < 2) {
+         Objects.requireNonNull(comparator, "comparator");
+         if (ArrayUtils.getLength(array) < 2) {
              return true;
          }
          T previous = array[0];
          int n = array.length;
          for (int i = 1; i < n; ++i) {
              T current = array[i];
              if (comparator.compare(previous, current) > 0) {
                  return false;
              }
              previous = current;
          }
          return true;
      }
  
      public static int lastIndexOf(boolean[] array, boolean valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(boolean[] array, boolean valueToFind, int startIndex) {
-         if (ArrayUtils.isEmpty(array)) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (ArrayUtils.isEmpty(array) || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(byte[] array, byte valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(byte[] array, byte valueToFind, int startIndex) {
-         if (array == null) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (array == null || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(char[] array, char valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(char[] array, char valueToFind, int startIndex) {
-         if (array == null) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (array == null || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(double[] array, double valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(double[] array, double valueToFind, double tolerance) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE, tolerance);
      }
  
      public static int lastIndexOf(double[] array, double valueToFind, int startIndex) {
-         if (ArrayUtils.isEmpty(array)) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (ArrayUtils.isEmpty(array) || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(double[] array, double valueToFind, int startIndex, double tolerance) {
-         if (ArrayUtils.isEmpty(array)) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (ArrayUtils.isEmpty(array) || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          double min = valueToFind - tolerance;
          double max = valueToFind + tolerance;
          for (int i = startIndex; i >= 0; --i) {
              if (!(array[i] >= min) || !(array[i] <= max)) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(float[] array, float valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(float[] array, float valueToFind, int startIndex) {
-         if (ArrayUtils.isEmpty(array)) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (ArrayUtils.isEmpty(array) || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(int[] array, int valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(int[] array, int valueToFind, int startIndex) {
-         if (array == null) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (array == null || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(long[] array, long valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(long[] array, long valueToFind, int startIndex) {
-         if (array == null) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (array == null || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
      public static int lastIndexOf(Object[] array, Object objectToFind) {
          return ArrayUtils.lastIndexOf(array, objectToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(Object[] array, Object objectToFind, int startIndex) {
-         block6: {
-             block5: {
-                 if (array == null) {
-                     return -1;
-                 }
-                 if (startIndex < 0) {
+         block5: {
+             block4: {
+                 if (array == null || startIndex < 0) {
                      return -1;
                  }
                  if (startIndex >= array.length) {
                      startIndex = array.length - 1;
                  }
-                 if (objectToFind != null) break block5;
+                 if (objectToFind != null) break block4;
                  for (int i = startIndex; i >= 0; --i) {
                      if (array[i] != null) continue;
                      return i;
                  }
-                 break block6;
+                 break block5;
              }
-             if (!array.getClass().getComponentType().isInstance(objectToFind)) break block6;
+             if (!array.getClass().getComponentType().isInstance(objectToFind)) break block5;
              for (int i = startIndex; i >= 0; --i) {
                  if (!objectToFind.equals(array[i])) continue;
                  return i;
              }
          }
          return -1;
      }
  
      public static int lastIndexOf(short[] array, short valueToFind) {
          return ArrayUtils.lastIndexOf(array, valueToFind, Integer.MAX_VALUE);
      }
  
      public static int lastIndexOf(short[] array, short valueToFind, int startIndex) {
-         if (array == null) {
-             return -1;
-         }
-         if (startIndex < 0) {
+         if (array == null || startIndex < 0) {
              return -1;
          }
          if (startIndex >= array.length) {
              startIndex = array.length - 1;
          }
          for (int i = startIndex; i >= 0; --i) {
              if (valueToFind != array[i]) continue;
              return i;
          }
          return -1;
      }
  
+     public static <T> T[] newInstance(Class<T> componentType, int length) {
+         return (Object[])Array.newInstance(componentType, length);
+     }
+ 
      public static boolean[] nullToEmpty(boolean[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_BOOLEAN_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_BOOLEAN_ARRAY : array;
      }
  
      public static Boolean[] nullToEmpty(Boolean[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_BOOLEAN_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_BOOLEAN_OBJECT_ARRAY : array;
      }
  
      public static byte[] nullToEmpty(byte[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_BYTE_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_BYTE_ARRAY : array;
      }
  
      public static Byte[] nullToEmpty(Byte[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_BYTE_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_BYTE_OBJECT_ARRAY : array;
      }
  
      public static char[] nullToEmpty(char[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_CHAR_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_CHAR_ARRAY : array;
      }
  
      public static Character[] nullToEmpty(Character[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_CHARACTER_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_CHARACTER_OBJECT_ARRAY : array;
      }
  
      public static Class<?>[] nullToEmpty(Class<?>[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_CLASS_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_CLASS_ARRAY : array;
      }
  
      public static double[] nullToEmpty(double[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_DOUBLE_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_DOUBLE_ARRAY : array;
      }
  
      public static Double[] nullToEmpty(Double[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_DOUBLE_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_DOUBLE_OBJECT_ARRAY : array;
      }
  
      public static float[] nullToEmpty(float[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_FLOAT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_FLOAT_ARRAY : array;
      }
  
      public static Float[] nullToEmpty(Float[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_FLOAT_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_FLOAT_OBJECT_ARRAY : array;
      }
  
      public static int[] nullToEmpty(int[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_INT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_INT_ARRAY : array;
      }
  
      public static Integer[] nullToEmpty(Integer[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_INTEGER_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_INTEGER_OBJECT_ARRAY : array;
      }
  
      public static long[] nullToEmpty(long[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_LONG_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_LONG_ARRAY : array;
      }
  
      public static Long[] nullToEmpty(Long[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_LONG_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_LONG_OBJECT_ARRAY : array;
      }
  
      public static Object[] nullToEmpty(Object[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_OBJECT_ARRAY : array;
      }
  
      public static short[] nullToEmpty(short[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_SHORT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_SHORT_ARRAY : array;
      }
  
      public static Short[] nullToEmpty(Short[] array) {
-         if (ArrayUtils.isEmpty((Object[])array)) {
-             return EMPTY_SHORT_OBJECT_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty((Object[])array) ? EMPTY_SHORT_OBJECT_ARRAY : array;
      }
  
      public static String[] nullToEmpty(String[] array) {
-         if (ArrayUtils.isEmpty(array)) {
-             return EMPTY_STRING_ARRAY;
-         }
-         return array;
+         return ArrayUtils.isEmpty(array) ? EMPTY_STRING_ARRAY : array;
      }
  
      public static <T> T[] nullToEmpty(T[] array, Class<T[]> type) {
          if (type == null) {
              throw new IllegalArgumentException("The type must not be null");
          }
          if (array == null) {
              return type.cast(Array.newInstance(type.getComponentType(), 0));
          }
          return array;
      }
  
+     private static ThreadLocalRandom random() {
+         return ThreadLocalRandom.current();
+     }
+ 
      public static boolean[] remove(boolean[] array, int index) {
          return (boolean[])ArrayUtils.remove((Object)array, index);
      }
  
      public static byte[] remove(byte[] array, int index) {
          return (byte[])ArrayUtils.remove((Object)array, index);
      }
  
      public static char[] remove(char[] array, int index) {
          return (char[])ArrayUtils.remove((Object)array, index);
      }
  
      public static double[] remove(double[] array, int index) {
          return (double[])ArrayUtils.remove((Object)array, index);
      }
  
      public static float[] remove(float[] array, int index) {
          return (float[])ArrayUtils.remove((Object)array, index);
      }
  
      public static int[] remove(int[] array, int index) {
          return (int[])ArrayUtils.remove((Object)array, index);
      }
  
      public static long[] remove(long[] array, int index) {
          return (long[])ArrayUtils.remove((Object)array, index);
      }
  
      private static Object remove(Object array, int index) {
          int length = ArrayUtils.getLength(array);
          if (index < 0 || index >= length) {
              throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
          }
          Object result = Array.newInstance(array.getClass().getComponentType(), length - 1);
          System.arraycopy(array, 0, result, 0, index);
          if (index < length - 1) {
              System.arraycopy(array, index + 1, result, index, length - index - 1);
          }
          return result;
      }
  
      public static short[] remove(short[] array, int index) {
          return (short[])ArrayUtils.remove((Object)array, index);
      }
  
      public static <T> T[] remove(T[] array, int index) {
          return (Object[])ArrayUtils.remove(array, index);
      }
  
      public static boolean[] removeAll(boolean[] array, int ... indices) {
          return (boolean[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static byte[] removeAll(byte[] array, int ... indices) {
          return (byte[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static char[] removeAll(char[] array, int ... indices) {
          return (char[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static double[] removeAll(double[] array, int ... indices) {
          return (double[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static float[] removeAll(float[] array, int ... indices) {
          return (float[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static int[] removeAll(int[] array, int ... indices) {
          return (int[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static long[] removeAll(long[] array, int ... indices) {
          return (long[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      static Object removeAll(Object array, BitSet indices) {
          int count;
          int set;
          if (array == null) {
              return null;
          }
          int srcLength = ArrayUtils.getLength(array);
          int removals = indices.cardinality();
          Object result = Array.newInstance(array.getClass().getComponentType(), srcLength - removals);
          int srcIndex = 0;
          int destIndex = 0;
          while ((set = indices.nextSetBit(srcIndex)) != -1) {
              count = set - srcIndex;
              if (count > 0) {
                  System.arraycopy(array, srcIndex, result, destIndex, count);
                  destIndex += count;
              }
              srcIndex = indices.nextClearBit(set);
          }
          count = srcLength - srcIndex;
          if (count > 0) {
              System.arraycopy(array, srcIndex, result, destIndex, count);
          }
          return result;
      }
  
      static Object removeAll(Object array, int ... indices) {
          int length = ArrayUtils.getLength(array);
          int diff = 0;
          int[] clonedIndices = ArraySorter.sort((int[])ArrayUtils.clone(indices));
          if (ArrayUtils.isNotEmpty(clonedIndices)) {
              int i = clonedIndices.length;
              int prevIndex = length;
              while (--i >= 0) {
                  int index = clonedIndices[i];
                  if (index < 0 || index >= length) {
                      throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
                  }
                  if (index >= prevIndex) continue;
                  ++diff;
                  prevIndex = index;
              }
          }
          Object result = Array.newInstance(array.getClass().getComponentType(), length - diff);
          if (diff < length) {
              int end = length;
              int dest = length - diff;
              for (int i = clonedIndices.length - 1; i >= 0; --i) {
                  int index = clonedIndices[i];
                  if (end - index > 1) {
                      int cp = end - index - 1;
                      System.arraycopy(array, index + 1, result, dest -= cp, cp);
                  }
                  end = index;
              }
              if (end > 0) {
                  System.arraycopy(array, 0, result, 0, end);
              }
          }
          return result;
      }
  
      public static short[] removeAll(short[] array, int ... indices) {
          return (short[])ArrayUtils.removeAll((Object)array, indices);
      }
  
      public static <T> T[] removeAll(T[] array, int ... indices) {
          return (Object[])ArrayUtils.removeAll(array, indices);
      }
  
      @Deprecated
      public static boolean[] removeAllOccurences(boolean[] array, boolean element) {
          return (boolean[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static byte[] removeAllOccurences(byte[] array, byte element) {
          return (byte[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static char[] removeAllOccurences(char[] array, char element) {
          return (char[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static double[] removeAllOccurences(double[] array, double element) {
          return (double[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static float[] removeAllOccurences(float[] array, float element) {
          return (float[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static int[] removeAllOccurences(int[] array, int element) {
          return (int[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static long[] removeAllOccurences(long[] array, long element) {
          return (long[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static short[] removeAllOccurences(short[] array, short element) {
          return (short[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      @Deprecated
      public static <T> T[] removeAllOccurences(T[] array, T element) {
          return (Object[])ArrayUtils.removeAll(array, ArrayUtils.indexesOf(array, element));
      }
  
      public static boolean[] removeAllOccurrences(boolean[] array, boolean element) {
          return (boolean[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static byte[] removeAllOccurrences(byte[] array, byte element) {
          return (byte[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static char[] removeAllOccurrences(char[] array, char element) {
          return (char[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static double[] removeAllOccurrences(double[] array, double element) {
          return (double[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static float[] removeAllOccurrences(float[] array, float element) {
          return (float[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static int[] removeAllOccurrences(int[] array, int element) {
          return (int[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static long[] removeAllOccurrences(long[] array, long element) {
          return (long[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static short[] removeAllOccurrences(short[] array, short element) {
          return (short[])ArrayUtils.removeAll((Object)array, ArrayUtils.indexesOf(array, element));
      }
  
      public static <T> T[] removeAllOccurrences(T[] array, T element) {
          return (Object[])ArrayUtils.removeAll(array, ArrayUtils.indexesOf(array, element));
      }
  
      public static boolean[] removeElement(boolean[] array, boolean element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static byte[] removeElement(byte[] array, byte element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static char[] removeElement(char[] array, char element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static double[] removeElement(double[] array, double element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static float[] removeElement(float[] array, float element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static int[] removeElement(int[] array, int element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static long[] removeElement(long[] array, long element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static short[] removeElement(short[] array, short element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static <T> T[] removeElement(T[] array, Object element) {
          int index = ArrayUtils.indexOf(array, element);
-         if (index == -1) {
-             return ArrayUtils.clone(array);
-         }
-         return ArrayUtils.remove(array, index);
+         return index == -1 ? ArrayUtils.clone(array) : ArrayUtils.remove(array, index);
      }
  
      public static boolean[] removeElements(boolean[] array, boolean ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Boolean, MutableInt> occurrences = new HashMap<Boolean, MutableInt>(2);
          for (boolean v : values) {
              Boolean boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              boolean key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (boolean[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static byte[] removeElements(byte[] array, byte ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Byte, MutableInt> occurrences = new HashMap<Byte, MutableInt>(values.length);
          for (byte v : values) {
              Byte boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              byte key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (byte[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static char[] removeElements(char[] array, char ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Character, MutableInt> occurrences = new HashMap<Character, MutableInt>(values.length);
          for (char v : values) {
              Character boxed = Character.valueOf(v);
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              char key = array[i];
              MutableInt count = (MutableInt)occurrences.get(Character.valueOf(key));
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(Character.valueOf(key));
              }
              toRemove.set(i);
          }
          return (char[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static double[] removeElements(double[] array, double ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Double, MutableInt> occurrences = new HashMap<Double, MutableInt>(values.length);
          for (double v : values) {
              Double boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              double key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (double[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static float[] removeElements(float[] array, float ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Float, MutableInt> occurrences = new HashMap<Float, MutableInt>(values.length);
          for (float v : values) {
              Float boxed = Float.valueOf(v);
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              float key = array[i];
              MutableInt count = (MutableInt)occurrences.get(Float.valueOf(key));
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(Float.valueOf(key));
              }
              toRemove.set(i);
          }
          return (float[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static int[] removeElements(int[] array, int ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Integer, MutableInt> occurrences = new HashMap<Integer, MutableInt>(values.length);
          for (int v : values) {
              Integer boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              int key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (int[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static long[] removeElements(long[] array, long ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Long, MutableInt> occurrences = new HashMap<Long, MutableInt>(values.length);
          for (long v : values) {
              Long boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              long key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (long[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      public static short[] removeElements(short[] array, short ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<Short, MutableInt> occurrences = new HashMap<Short, MutableInt>(values.length);
          for (short v : values) {
              Short boxed = v;
              MutableInt count = (MutableInt)occurrences.get(boxed);
              if (count == null) {
                  occurrences.put(boxed, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              short key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          return (short[])ArrayUtils.removeAll((Object)array, toRemove);
      }
  
      @SafeVarargs
      public static <T> T[] removeElements(T[] array, T ... values) {
          if (ArrayUtils.isEmpty(array) || ArrayUtils.isEmpty(values)) {
              return ArrayUtils.clone(array);
          }
          HashMap<T, MutableInt> occurrences = new HashMap<T, MutableInt>(values.length);
          for (T v : values) {
              MutableInt count = (MutableInt)occurrences.get(v);
              if (count == null) {
                  occurrences.put(v, new MutableInt(1));
                  continue;
              }
              count.increment();
          }
          BitSet toRemove = new BitSet();
          for (int i = 0; i < array.length; ++i) {
              T key = array[i];
              MutableInt count = (MutableInt)occurrences.get(key);
              if (count == null) continue;
              if (count.decrementAndGet() == 0) {
                  occurrences.remove(key);
              }
              toRemove.set(i);
          }
          Object[] result = (Object[])ArrayUtils.removeAll(array, toRemove);
          return result;
      }
  
      public static void reverse(boolean[] array) {
          if (array == null) {
              return;
          }
          ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(boolean[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              boolean tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(byte[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(byte[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              byte tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(char[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(char[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              char tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(double[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(double[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              double tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(float[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(float[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              float tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(int[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(int[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              int tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(long[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(long[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              long tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(Object[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(Object[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              Object tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
      public static void reverse(short[] array) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.reverse(array, 0, array.length);
          }
-         ArrayUtils.reverse(array, 0, array.length);
      }
  
      public static void reverse(short[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return;
          }
          int i = Math.max(startIndexInclusive, 0);
          for (int j = Math.min(array.length, endIndexExclusive) - 1; j > i; --j, ++i) {
              short tmp = array[j];
              array[j] = array[i];
              array[i] = tmp;
          }
      }
  
+     public static <T> T[] setAll(T[] array, IntFunction<? extends T> generator) {
+         if (array != null && generator != null) {
+             Arrays.setAll(array, generator);
+         }
+         return array;
+     }
+ 
+     public static <T> T[] setAll(T[] array, Supplier<? extends T> generator) {
+         if (array != null && generator != null) {
+             for (int i = 0; i < array.length; ++i) {
+                 array[i] = generator.get();
+             }
+         }
+         return array;
+     }
+ 
      public static void shift(boolean[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(boolean[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(byte[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(byte[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(char[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(char[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(double[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(double[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(float[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(float[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(int[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(int[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(long[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(long[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(Object[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(Object[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shift(short[] array, int offset) {
-         if (array == null) {
-             return;
+         if (array != null) {
+             ArrayUtils.shift(array, 0, array.length, offset);
          }
-         ArrayUtils.shift(array, 0, array.length, offset);
      }
  
      public static void shift(short[] array, int startIndexInclusive, int endIndexExclusive, int offset) {
          int n;
-         if (array == null) {
-             return;
-         }
-         if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
+         if (array == null || startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0) {
              return;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive >= array.length) {
              endIndexExclusive = array.length;
          }
          if ((n = endIndexExclusive - startIndexInclusive) <= 1) {
              return;
          }
          if ((offset %= n) < 0) {
              offset += n;
          }
          while (n > 1 && offset > 0) {
-             int n_offset = n - offset;
-             if (offset > n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
+             int nOffset = n - offset;
+             if (offset > nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n - nOffset, nOffset);
                  n = offset;
-                 offset -= n_offset;
+                 offset -= nOffset;
                  continue;
              }
-             if (offset < n_offset) {
-                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             if (offset < nOffset) {
+                 ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
                  startIndexInclusive += offset;
-                 n = n_offset;
+                 n = nOffset;
                  continue;
              }
-             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
+             ArrayUtils.swap(array, startIndexInclusive, startIndexInclusive + nOffset, offset);
              break;
          }
      }
  
      public static void shuffle(boolean[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(boolean[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(byte[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(byte[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(char[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(char[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(double[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(double[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(float[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(float[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(int[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(int[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(long[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(long[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(Object[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(Object[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static void shuffle(short[] array) {
-         ArrayUtils.shuffle(array, new Random());
+         ArrayUtils.shuffle(array, (Random)ArrayUtils.random());
      }
  
      public static void shuffle(short[] array, Random random) {
          for (int i = array.length; i > 1; --i) {
              ArrayUtils.swap(array, i - 1, random.nextInt(i), 1);
          }
      }
  
      public static boolean[] subarray(boolean[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_BOOLEAN_ARRAY;
          }
          boolean[] subarray = new boolean[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static byte[] subarray(byte[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_BYTE_ARRAY;
          }
          byte[] subarray = new byte[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static char[] subarray(char[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_CHAR_ARRAY;
          }
          char[] subarray = new char[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static double[] subarray(double[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_DOUBLE_ARRAY;
          }
          double[] subarray = new double[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static float[] subarray(float[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_FLOAT_ARRAY;
          }
          float[] subarray = new float[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static int[] subarray(int[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_INT_ARRAY;
          }
          int[] subarray = new int[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static long[] subarray(long[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_LONG_ARRAY;
          }
          long[] subarray = new long[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static short[] subarray(short[] array, int startIndexInclusive, int endIndexExclusive) {
          int newSize;
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          if ((newSize = endIndexExclusive - startIndexInclusive) <= 0) {
              return EMPTY_SHORT_ARRAY;
          }
          short[] subarray = new short[newSize];
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static <T> T[] subarray(T[] array, int startIndexInclusive, int endIndexExclusive) {
          if (array == null) {
              return null;
          }
          if (startIndexInclusive < 0) {
              startIndexInclusive = 0;
          }
          if (endIndexExclusive > array.length) {
              endIndexExclusive = array.length;
          }
          int newSize = endIndexExclusive - startIndexInclusive;
-         Class<?> type = array.getClass().getComponentType();
+         Class<T> type = ArrayUtils.getComponentType(array);
          if (newSize <= 0) {
-             Object[] emptyArray = (Object[])Array.newInstance(type, 0);
-             return emptyArray;
+             return ArrayUtils.newInstance(type, 0);
          }
-         Object[] subarray = (Object[])Array.newInstance(type, newSize);
+         T[] subarray = ArrayUtils.newInstance(type, newSize);
          System.arraycopy(array, startIndexInclusive, subarray, 0, newSize);
          return subarray;
      }
  
      public static void swap(boolean[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(boolean[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              boolean aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(byte[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(byte[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              byte aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(char[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(char[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              char aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(double[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(double[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              double aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(float[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(float[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              float aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(int[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(int[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              int aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(long[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(long[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              long aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(Object[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(Object[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              Object aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static void swap(short[] array, int offset1, int offset2) {
-         if (ArrayUtils.isEmpty(array)) {
-             return;
-         }
          ArrayUtils.swap(array, offset1, offset2, 1);
      }
  
      public static void swap(short[] array, int offset1, int offset2, int len) {
          if (ArrayUtils.isEmpty(array) || offset1 >= array.length || offset2 >= array.length) {
              return;
          }
          if (offset1 < 0) {
              offset1 = 0;
          }
          if (offset2 < 0) {
              offset2 = 0;
          }
          if (offset1 == offset2) {
              return;
          }
          len = Math.min(Math.min(len, array.length - offset1), array.length - offset2);
          int i = 0;
          while (i < len) {
              short aux = array[offset1];
              array[offset1] = array[offset2];
              array[offset2] = aux;
              ++i;
              ++offset1;
              ++offset2;
          }
      }
  
      public static <T> T[] toArray(T ... items) {
          return items;
      }
  
      public static Map<Object, Object> toMap(Object[] array) {
          if (array == null) {
              return null;
          }
          HashMap<Object, Object> map = new HashMap<Object, Object>((int)((double)array.length * 1.5));
          for (int i = 0; i < array.length; ++i) {
              Object[] entry;
              Object object = array[i];
              if (object instanceof Map.Entry) {
                  entry = (Object[])object;
                  map.put(entry.getKey(), entry.getValue());
                  continue;
              }
              if (object instanceof Object[]) {
                  entry = (Object[])object;
                  if (entry.length < 2) {
                      throw new IllegalArgumentException("Array element " + i + ", '" + object + "', has a length less than 2");
                  }
                  map.put(entry[0], entry[1]);
                  continue;
              }
              throw new IllegalArgumentException("Array element " + i + ", '" + object + "', is neither of type Map.Entry nor an Array");
          }
          return map;
      }
  
      public static Boolean[] toObject(boolean[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_BOOLEAN_OBJECT_ARRAY;
          }
          Boolean[] result = new Boolean[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i] ? Boolean.TRUE : Boolean.FALSE;
-         }
-         return result;
+         return ArrayUtils.setAll(result, (int i) -> array[i] ? Boolean.TRUE : Boolean.FALSE);
      }
  
      public static Byte[] toObject(byte[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_BYTE_OBJECT_ARRAY;
          }
-         Byte[] result = new Byte[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.setAll(new Byte[array.length], (int i) -> array[i]);
      }
  
      public static Character[] toObject(char[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_CHARACTER_OBJECT_ARRAY;
          }
-         Character[] result = new Character[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = Character.valueOf(array[i]);
-         }
-         return result;
+         return ArrayUtils.setAll(new Character[array.length], (int i) -> Character.valueOf(array[i]));
      }
  
      public static Double[] toObject(double[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_DOUBLE_OBJECT_ARRAY;
          }
-         Double[] result = new Double[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.setAll(new Double[array.length], (int i) -> array[i]);
      }
  
      public static Float[] toObject(float[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_FLOAT_OBJECT_ARRAY;
          }
-         Float[] result = new Float[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = Float.valueOf(array[i]);
-         }
-         return result;
+         return ArrayUtils.setAll(new Float[array.length], (int i) -> Float.valueOf(array[i]));
      }
  
      public static Integer[] toObject(int[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_INTEGER_OBJECT_ARRAY;
          }
-         Integer[] result = new Integer[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.setAll(new Integer[array.length], (int i) -> array[i]);
      }
  
      public static Long[] toObject(long[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_LONG_OBJECT_ARRAY;
          }
-         Long[] result = new Long[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.setAll(new Long[array.length], (int i) -> array[i]);
      }
  
      public static Short[] toObject(short[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_SHORT_OBJECT_ARRAY;
          }
-         Short[] result = new Short[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.setAll(new Short[array.length], (int i) -> array[i]);
      }
  
      public static boolean[] toPrimitive(Boolean[] array) {
-         if (array == null) {
-             return null;
-         }
-         if (array.length == 0) {
-             return EMPTY_BOOLEAN_ARRAY;
-         }
-         boolean[] result = new boolean[array.length];
-         for (int i = 0; i < array.length; ++i) {
-             result[i] = array[i];
-         }
-         return result;
+         return ArrayUtils.toPrimitive(array, false);
      }
  
      public static boolean[] toPrimitive(Boolean[] array, boolean valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_BOOLEAN_ARRAY;
          }
          boolean[] result = new boolean[array.length];
          for (int i = 0; i < array.length; ++i) {
              Boolean b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static byte[] toPrimitive(Byte[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_BYTE_ARRAY;
          }
          byte[] result = new byte[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i];
          }
          return result;
      }
  
      public static byte[] toPrimitive(Byte[] array, byte valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_BYTE_ARRAY;
          }
          byte[] result = new byte[array.length];
          for (int i = 0; i < array.length; ++i) {
              Byte b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static char[] toPrimitive(Character[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_CHAR_ARRAY;
          }
          char[] result = new char[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i].charValue();
          }
          return result;
      }
  
      public static char[] toPrimitive(Character[] array, char valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_CHAR_ARRAY;
          }
          char[] result = new char[array.length];
          for (int i = 0; i < array.length; ++i) {
              Character b = array[i];
              result[i] = b == null ? valueForNull : b.charValue();
          }
          return result;
      }
  
      public static double[] toPrimitive(Double[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_DOUBLE_ARRAY;
          }
          double[] result = new double[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i];
          }
          return result;
      }
  
      public static double[] toPrimitive(Double[] array, double valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_DOUBLE_ARRAY;
          }
          double[] result = new double[array.length];
          for (int i = 0; i < array.length; ++i) {
              Double b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static float[] toPrimitive(Float[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_FLOAT_ARRAY;
          }
          float[] result = new float[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i].floatValue();
          }
          return result;
      }
  
      public static float[] toPrimitive(Float[] array, float valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_FLOAT_ARRAY;
          }
          float[] result = new float[array.length];
          for (int i = 0; i < array.length; ++i) {
              Float b = array[i];
              result[i] = b == null ? valueForNull : b.floatValue();
          }
          return result;
      }
  
      public static int[] toPrimitive(Integer[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_INT_ARRAY;
          }
          int[] result = new int[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i];
          }
          return result;
      }
  
      public static int[] toPrimitive(Integer[] array, int valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_INT_ARRAY;
          }
          int[] result = new int[array.length];
          for (int i = 0; i < array.length; ++i) {
              Integer b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static long[] toPrimitive(Long[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_LONG_ARRAY;
          }
          long[] result = new long[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i];
          }
          return result;
      }
  
      public static long[] toPrimitive(Long[] array, long valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_LONG_ARRAY;
          }
          long[] result = new long[array.length];
          for (int i = 0; i < array.length; ++i) {
              Long b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static Object toPrimitive(Object array) {
          if (array == null) {
              return null;
          }
          Class<?> ct = array.getClass().getComponentType();
          Class pt = ClassUtils.wrapperToPrimitive(ct);
          if (Boolean.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Boolean[])array);
          }
          if (Character.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Character[])array);
          }
          if (Byte.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Byte[])array);
          }
          if (Integer.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Integer[])array);
          }
          if (Long.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Long[])array);
          }
          if (Short.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Short[])array);
          }
          if (Double.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Double[])array);
          }
          if (Float.TYPE.equals(pt)) {
              return ArrayUtils.toPrimitive((Float[])array);
          }
          return array;
      }
  
      public static short[] toPrimitive(Short[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_SHORT_ARRAY;
          }
          short[] result = new short[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i];
          }
          return result;
      }
  
      public static short[] toPrimitive(Short[] array, short valueForNull) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_SHORT_ARRAY;
          }
          short[] result = new short[array.length];
          for (int i = 0; i < array.length; ++i) {
              Short b = array[i];
              result[i] = b == null ? valueForNull : b;
          }
          return result;
      }
  
      public static String toString(Object array) {
          return ArrayUtils.toString(array, "{}");
      }
  
      public static String toString(Object array, String stringIfNull) {
          if (array == null) {
              return stringIfNull;
          }
          return new ToStringBuilder(array, ToStringStyle.SIMPLE_STYLE).append(array).toString();
      }
  
      public static String[] toStringArray(Object[] array) {
          if (array == null) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_STRING_ARRAY;
          }
          String[] result = new String[array.length];
          for (int i = 0; i < array.length; ++i) {
              result[i] = array[i].toString();
          }
          return result;
      }
  
      public static String[] toStringArray(Object[] array, String valueForNullElements) {
          if (null == array) {
              return null;
          }
          if (array.length == 0) {
              return EMPTY_STRING_ARRAY;
          }
          String[] result = new String[array.length];
          for (int i = 0; i < array.length; ++i) {
-             Object object = array[i];
-             result[i] = object == null ? valueForNullElements : object.toString();
+             result[i] = Objects.toString(array[i], valueForNullElements);
          }
          return result;
      }
  }
  

```

### org/apache/commons/lang3/BitField.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.apache.commons.lang3;
  
  public class BitField {
-     private final int _mask;
-     private final int _shift_count;
+     private final int mask;
+     private final int shiftCount;
  
      public BitField(int mask) {
-         this._mask = mask;
-         this._shift_count = mask == 0 ? 0 : Integer.numberOfTrailingZeros(mask);
+         this.mask = mask;
+         this.shiftCount = mask == 0 ? 0 : Integer.numberOfTrailingZeros(mask);
      }
  
-     public int getValue(int holder) {
-         return this.getRawValue(holder) >> this._shift_count;
+     public int clear(int holder) {
+         return holder & ~this.mask;
      }
  
-     public short getShortValue(short holder) {
-         return (short)this.getValue(holder);
+     public byte clearByte(byte holder) {
+         return (byte)this.clear(holder);
      }
  
+     public short clearShort(short holder) {
+         return (short)this.clear(holder);
+     }
+ 
      public int getRawValue(int holder) {
-         return holder & this._mask;
+         return holder & this.mask;
      }
  
      public short getShortRawValue(short holder) {
          return (short)this.getRawValue(holder);
      }
  
-     public boolean isSet(int holder) {
-         return (holder & this._mask) != 0;
+     public short getShortValue(short holder) {
+         return (short)this.getValue(holder);
      }
  
-     public boolean isAllSet(int holder) {
-         return (holder & this._mask) == this._mask;
+     public int getValue(int holder) {
+         return this.getRawValue(holder) >> this.shiftCount;
      }
  
-     public int setValue(int holder, int value) {
-         return holder & ~this._mask | value << this._shift_count & this._mask;
+     public boolean isAllSet(int holder) {
+         return (holder & this.mask) == this.mask;
      }
  
-     public short setShortValue(short holder, short value) {
-         return (short)this.setValue(holder, value);
+     public boolean isSet(int holder) {
+         return (holder & this.mask) != 0;
      }
  
-     public int clear(int holder) {
-         return holder & ~this._mask;
+     public int set(int holder) {
+         return holder | this.mask;
      }
  
-     public short clearShort(short holder) {
-         return (short)this.clear(holder);
+     public int setBoolean(int holder, boolean flag) {
+         return flag ? this.set(holder) : this.clear(holder);
      }
  
-     public byte clearByte(byte holder) {
-         return (byte)this.clear(holder);
+     public byte setByte(byte holder) {
+         return (byte)this.set(holder);
      }
  
-     public int set(int holder) {
-         return holder | this._mask;
+     public byte setByteBoolean(byte holder, boolean flag) {
+         return flag ? this.setByte(holder) : this.clearByte(holder);
      }
  
      public short setShort(short holder) {
          return (short)this.set(holder);
      }
  
-     public byte setByte(byte holder) {
-         return (byte)this.set(holder);
-     }
- 
-     public int setBoolean(int holder, boolean flag) {
-         return flag ? this.set(holder) : this.clear(holder);
-     }
- 
      public short setShortBoolean(short holder, boolean flag) {
          return flag ? this.setShort(holder) : this.clearShort(holder);
      }
  
-     public byte setByteBoolean(byte holder, boolean flag) {
-         return flag ? this.setByte(holder) : this.clearByte(holder);
+     public short setShortValue(short holder, short value) {
+         return (short)this.setValue(holder, value);
      }
+ 
+     public int setValue(int holder, int value) {
+         return holder & ~this.mask | value << this.shiftCount & this.mask;
+     }
  }
  

```

### org/apache/commons/lang3/BooleanUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.ArrayUtils
   *  org.apache.commons.lang3.ObjectUtils
   *  org.apache.commons.lang3.math.NumberUtils
   */
  package org.apache.commons.lang3;
  
+ import java.util.Arrays;
+ import java.util.Collections;
+ import java.util.List;
+ import java.util.function.Consumer;
  import org.apache.commons.lang3.ArrayUtils;
  import org.apache.commons.lang3.ObjectUtils;
  import org.apache.commons.lang3.math.NumberUtils;
  
  public class BooleanUtils {
+     private static final List<Boolean> BOOLEAN_LIST = Collections.unmodifiableList(Arrays.asList(Boolean.FALSE, Boolean.TRUE));
      public static final String FALSE = "false";
      public static final String NO = "no";
      public static final String OFF = "off";
      public static final String ON = "on";
      public static final String TRUE = "true";
      public static final String YES = "yes";
  
      public static boolean and(boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
          for (boolean element : array) {
              if (element) continue;
              return false;
          }
          return true;
      }
  
      public static Boolean and(Boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
-         try {
-             boolean[] primitive = ArrayUtils.toPrimitive((Boolean[])array);
-             return BooleanUtils.and(primitive) ? Boolean.TRUE : Boolean.FALSE;
-         }
-         catch (NullPointerException ex) {
-             throw new IllegalArgumentException("The array must not contain any null elements");
-         }
+         return BooleanUtils.and(ArrayUtils.toPrimitive((Boolean[])array)) ? Boolean.TRUE : Boolean.FALSE;
      }
  
      public static Boolean[] booleanValues() {
          return new Boolean[]{Boolean.FALSE, Boolean.TRUE};
      }
  
      public static int compare(boolean x, boolean y) {
          if (x == y) {
              return 0;
          }
          return x ? 1 : -1;
      }
  
+     public static void forEach(Consumer<Boolean> action) {
+         BooleanUtils.values().forEach(action);
+     }
+ 
      public static boolean isFalse(Boolean bool) {
          return Boolean.FALSE.equals(bool);
      }
  
      public static boolean isNotFalse(Boolean bool) {
          return !BooleanUtils.isFalse(bool);
      }
  
      public static boolean isNotTrue(Boolean bool) {
          return !BooleanUtils.isTrue(bool);
      }
  
      public static boolean isTrue(Boolean bool) {
          return Boolean.TRUE.equals(bool);
      }
  
      public static Boolean negate(Boolean bool) {
          if (bool == null) {
              return null;
          }
          return bool != false ? Boolean.FALSE : Boolean.TRUE;
      }
  
+     public static boolean oneHot(boolean ... array) {
+         ObjectUtils.requireNonEmpty((Object)array, (String)"array");
+         boolean result = false;
+         for (boolean element : array) {
+             if (!element) continue;
+             if (result) {
+                 return false;
+             }
+             result = true;
+         }
+         return result;
+     }
+ 
+     public static Boolean oneHot(Boolean ... array) {
+         return BooleanUtils.oneHot(ArrayUtils.toPrimitive((Boolean[])array));
+     }
+ 
      public static boolean or(boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
          for (boolean element : array) {
              if (!element) continue;
              return true;
          }
          return false;
      }
  
      public static Boolean or(Boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
-         try {
-             boolean[] primitive = ArrayUtils.toPrimitive((Boolean[])array);
-             return BooleanUtils.or(primitive) ? Boolean.TRUE : Boolean.FALSE;
-         }
-         catch (NullPointerException ex) {
-             throw new IllegalArgumentException("The array must not contain any null elements");
-         }
+         return BooleanUtils.or(ArrayUtils.toPrimitive((Boolean[])array)) ? Boolean.TRUE : Boolean.FALSE;
      }
  
      public static boolean[] primitiveValues() {
          return new boolean[]{false, true};
      }
  
      public static boolean toBoolean(Boolean bool) {
          return bool != null && bool != false;
      }
  
      public static boolean toBoolean(int value) {
          return value != 0;
      }
  
      public static boolean toBoolean(int value, int trueValue, int falseValue) {
          if (value == trueValue) {
              return true;
          }
          if (value == falseValue) {
              return false;
          }
          throw new IllegalArgumentException("The Integer did not match either specified value");
      }
  
      public static boolean toBoolean(Integer value, Integer trueValue, Integer falseValue) {
          if (value == null) {
              if (trueValue == null) {
                  return true;
              }
              if (falseValue == null) {
                  return false;
              }
          } else {
              if (value.equals(trueValue)) {
                  return true;
              }
              if (value.equals(falseValue)) {
                  return false;
              }
          }
          throw new IllegalArgumentException("The Integer did not match either specified value");
      }
  
      public static boolean toBoolean(String str) {
          return BooleanUtils.toBooleanObject(str) == Boolean.TRUE;
      }
  
      public static boolean toBoolean(String str, String trueString, String falseString) {
          if (str == trueString) {
              return true;
          }
          if (str == falseString) {
              return false;
          }
          if (str != null) {
              if (str.equals(trueString)) {
                  return true;
              }
              if (str.equals(falseString)) {
                  return false;
              }
          }
          throw new IllegalArgumentException("The String did not match either specified value");
      }
  
      public static boolean toBooleanDefaultIfNull(Boolean bool, boolean valueIfNull) {
          if (bool == null) {
              return valueIfNull;
          }
          return bool;
      }
  
      public static Boolean toBooleanObject(int value) {
          return value == 0 ? Boolean.FALSE : Boolean.TRUE;
      }
  
      public static Boolean toBooleanObject(int value, int trueValue, int falseValue, int nullValue) {
          if (value == trueValue) {
              return Boolean.TRUE;
          }
          if (value == falseValue) {
              return Boolean.FALSE;
          }
          if (value == nullValue) {
              return null;
          }
          throw new IllegalArgumentException("The Integer did not match any specified value");
      }
  
      public static Boolean toBooleanObject(Integer value) {
          if (value == null) {
              return null;
          }
          return value == 0 ? Boolean.FALSE : Boolean.TRUE;
      }
  
      public static Boolean toBooleanObject(Integer value, Integer trueValue, Integer falseValue, Integer nullValue) {
          if (value == null) {
              if (trueValue == null) {
                  return Boolean.TRUE;
              }
              if (falseValue == null) {
                  return Boolean.FALSE;
              }
              if (nullValue == null) {
                  return null;
              }
          } else {
              if (value.equals(trueValue)) {
                  return Boolean.TRUE;
              }
              if (value.equals(falseValue)) {
                  return Boolean.FALSE;
              }
              if (value.equals(nullValue)) {
                  return null;
              }
          }
          throw new IllegalArgumentException("The Integer did not match any specified value");
      }
  
      public static Boolean toBooleanObject(String str) {
          if (str == TRUE) {
              return Boolean.TRUE;
          }
          if (str == null) {
              return null;
          }
          switch (str.length()) {
              case 1: {
                  char ch0 = str.charAt(0);
                  if (ch0 == 'y' || ch0 == 'Y' || ch0 == 't' || ch0 == 'T' || ch0 == '1') {
                      return Boolean.TRUE;
                  }
                  if (ch0 != 'n' && ch0 != 'N' && ch0 != 'f' && ch0 != 'F' && ch0 != '0') break;
                  return Boolean.FALSE;
              }
              case 2: {
                  char ch0 = str.charAt(0);
                  char ch1 = str.charAt(1);
                  if (!(ch0 != 'o' && ch0 != 'O' || ch1 != 'n' && ch1 != 'N')) {
                      return Boolean.TRUE;
                  }
                  if (ch0 != 'n' && ch0 != 'N' || ch1 != 'o' && ch1 != 'O') break;
                  return Boolean.FALSE;
              }
              case 3: {
                  char ch0 = str.charAt(0);
                  char ch1 = str.charAt(1);
                  char ch2 = str.charAt(2);
                  if (!(ch0 != 'y' && ch0 != 'Y' || ch1 != 'e' && ch1 != 'E' || ch2 != 's' && ch2 != 'S')) {
                      return Boolean.TRUE;
                  }
                  if (ch0 != 'o' && ch0 != 'O' || ch1 != 'f' && ch1 != 'F' || ch2 != 'f' && ch2 != 'F') break;
                  return Boolean.FALSE;
              }
              case 4: {
                  char ch0 = str.charAt(0);
                  char ch1 = str.charAt(1);
                  char ch2 = str.charAt(2);
                  char ch3 = str.charAt(3);
                  if (ch0 != 't' && ch0 != 'T' || ch1 != 'r' && ch1 != 'R' || ch2 != 'u' && ch2 != 'U' || ch3 != 'e' && ch3 != 'E') break;
                  return Boolean.TRUE;
              }
              case 5: {
                  char ch0 = str.charAt(0);
                  char ch1 = str.charAt(1);
                  char ch2 = str.charAt(2);
                  char ch3 = str.charAt(3);
                  char ch4 = str.charAt(4);
                  if (ch0 != 'f' && ch0 != 'F' || ch1 != 'a' && ch1 != 'A' || ch2 != 'l' && ch2 != 'L' || ch3 != 's' && ch3 != 'S' || ch4 != 'e' && ch4 != 'E') break;
                  return Boolean.FALSE;
              }
          }
          return null;
      }
  
      public static Boolean toBooleanObject(String str, String trueString, String falseString, String nullString) {
          if (str == null) {
              if (trueString == null) {
                  return Boolean.TRUE;
              }
              if (falseString == null) {
                  return Boolean.FALSE;
              }
              if (nullString == null) {
                  return null;
              }
          } else {
              if (str.equals(trueString)) {
                  return Boolean.TRUE;
              }
              if (str.equals(falseString)) {
                  return Boolean.FALSE;
              }
              if (str.equals(nullString)) {
                  return null;
              }
          }
          throw new IllegalArgumentException("The String did not match any specified value");
      }
  
      public static int toInteger(boolean bool) {
          return bool ? 1 : 0;
      }
  
      public static int toInteger(boolean bool, int trueValue, int falseValue) {
          return bool ? trueValue : falseValue;
      }
  
      public static int toInteger(Boolean bool, int trueValue, int falseValue, int nullValue) {
          if (bool == null) {
              return nullValue;
          }
          return bool != false ? trueValue : falseValue;
      }
  
      public static Integer toIntegerObject(boolean bool) {
          return bool ? NumberUtils.INTEGER_ONE : NumberUtils.INTEGER_ZERO;
      }
  
      public static Integer toIntegerObject(boolean bool, Integer trueValue, Integer falseValue) {
          return bool ? trueValue : falseValue;
      }
  
      public static Integer toIntegerObject(Boolean bool) {
          if (bool == null) {
              return null;
          }
          return bool != false ? NumberUtils.INTEGER_ONE : NumberUtils.INTEGER_ZERO;
      }
  
      public static Integer toIntegerObject(Boolean bool, Integer trueValue, Integer falseValue, Integer nullValue) {
          if (bool == null) {
              return nullValue;
          }
          return bool != false ? trueValue : falseValue;
      }
  
      public static String toString(boolean bool, String trueString, String falseString) {
          return bool ? trueString : falseString;
      }
  
      public static String toString(Boolean bool, String trueString, String falseString, String nullString) {
          if (bool == null) {
              return nullString;
          }
          return bool != false ? trueString : falseString;
      }
  
      public static String toStringOnOff(boolean bool) {
          return BooleanUtils.toString(bool, ON, OFF);
      }
  
      public static String toStringOnOff(Boolean bool) {
          return BooleanUtils.toString(bool, ON, OFF, null);
      }
  
      public static String toStringTrueFalse(boolean bool) {
          return BooleanUtils.toString(bool, TRUE, FALSE);
      }
  
      public static String toStringTrueFalse(Boolean bool) {
          return BooleanUtils.toString(bool, TRUE, FALSE, null);
      }
  
      public static String toStringYesNo(boolean bool) {
          return BooleanUtils.toString(bool, YES, NO);
      }
  
      public static String toStringYesNo(Boolean bool) {
          return BooleanUtils.toString(bool, YES, NO, null);
      }
  
+     public static List<Boolean> values() {
+         return BOOLEAN_LIST;
+     }
+ 
      public static boolean xor(boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
          boolean result = false;
          for (boolean element : array) {
              result ^= element;
          }
          return result;
      }
  
      public static Boolean xor(Boolean ... array) {
          ObjectUtils.requireNonEmpty((Object)array, (String)"array");
-         try {
-             boolean[] primitive = ArrayUtils.toPrimitive((Boolean[])array);
-             return BooleanUtils.xor(primitive) ? Boolean.TRUE : Boolean.FALSE;
-         }
-         catch (NullPointerException ex) {
-             throw new IllegalArgumentException("The array must not contain any null elements");
-         }
+         return BooleanUtils.xor(ArrayUtils.toPrimitive((Boolean[])array)) ? Boolean.TRUE : Boolean.FALSE;
      }
  }
  

```

### org/apache/commons/lang3/CharEncoding.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.apache.commons.lang3;
  
  import java.nio.charset.Charset;
  import java.nio.charset.IllegalCharsetNameException;
  
  @Deprecated
  public class CharEncoding {
      public static final String ISO_8859_1 = "ISO-8859-1";
      public static final String US_ASCII = "US-ASCII";
      public static final String UTF_16 = "UTF-16";
      public static final String UTF_16BE = "UTF-16BE";
      public static final String UTF_16LE = "UTF-16LE";
      public static final String UTF_8 = "UTF-8";
  
      @Deprecated
      public static boolean isSupported(String name) {
          if (name == null) {
              return false;
          }
          try {
              return Charset.isSupported(name);
          }
          catch (IllegalCharsetNameException ex) {
              return false;
          }
      }
  }
  

```

### org/apache/commons/lang3/CharRange$1.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.apache.commons.lang3;
  
  static class CharRange.1 {
  }
  

```

### org/apache/commons/lang3/CharRange$CharacterIterator.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.CharRange
   */
  package org.apache.commons.lang3;
  
  import java.util.Iterator;
  import java.util.NoSuchElementException;
  import org.apache.commons.lang3.CharRange;
  
  /*
   * Exception performing whole class analysis ignored.
   */
- private static class CharRange.CharacterIterator
+ private static final class CharRange.CharacterIterator
  implements Iterator<Character> {
      private char current;
      private final CharRange range;
      private boolean hasNext;
  
      private CharRange.CharacterIterator(CharRange r) {
          this.range = r;
          this.hasNext = true;
-         if (CharRange.access$100((CharRange)this.range)) {
-             if (CharRange.access$200((CharRange)this.range) == '\u0000') {
-                 if (CharRange.access$300((CharRange)this.range) == '\uffff') {
+         if (CharRange.access$000((CharRange)this.range)) {
+             if (CharRange.access$100((CharRange)this.range) == '\u0000') {
+                 if (CharRange.access$200((CharRange)this.range) == '\uffff') {
                      this.hasNext = false;
                  } else {
-                     this.current = (char)(CharRange.access$300((CharRange)this.range) + '\u0001');
+                     this.current = (char)(CharRange.access$200((CharRange)this.range) + '\u0001');
                  }
              } else {
                  this.current = '\u0000';
              }
          } else {
-             this.current = CharRange.access$200((CharRange)this.range);
-         }
-     }
- 
-     private void prepareNext() {
-         if (CharRange.access$100((CharRange)this.range)) {
-             if (this.current == '\uffff') {
-                 this.hasNext = false;
-             } else if (this.current + '\u0001' == CharRange.access$200((CharRange)this.range)) {
-                 if (CharRange.access$300((CharRange)this.range) == '\uffff') {
-                     this.hasNext = false;
-                 } else {
-                     this.current = (char)(CharRange.access$300((CharRange)this.range) + '\u0001');
-                 }
-             } else {
-                 this.current = (char)(this.current + '\u0001');
-             }
-         } else if (this.current < CharRange.access$300((CharRange)this.range)) {
-             this.current = (char)(this.current + '\u0001');
-         } else {
-             this.hasNext = false;
+             this.current = CharRange.access$100((CharRange)this.range);
          }
      }
  
      @Override
      public boolean hasNext() {
          return this.hasNext;
      }
  
      @Override
      public Character next() {
          if (!this.hasNext) {
              throw new NoSuchElementException();
          }
          char cur = this.current;
          this.prepareNext();
          return Character.valueOf(cur);
      }
  
+     private void prepareNext() {
+         if (CharRange.access$000((CharRange)this.range)) {
+             if (this.current == '\uffff') {
+                 this.hasNext = false;
+             } else if (this.current + '\u0001' == CharRange.access$100((CharRange)this.range)) {
+                 if (CharRange.access$200((CharRange)this.range) == '\uffff') {
+                     this.hasNext = false;
+                 } else {
+                     this.current = (char)(CharRange.access$200((CharRange)this.range) + '\u0001');
+                 }
+             } else {
+                 this.current = (char)(this.current + '\u0001');
+             }
+         } else if (this.current < CharRange.access$200((CharRange)this.range)) {
+             this.current = (char)(this.current + '\u0001');
+         } else {
+             this.hasNext = false;
+         }
+     }
+ 
      @Override
      public void remove() {
          throw new UnsupportedOperationException();
      }
  }
  

```

### org/apache/commons/lang3/CharRange.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.CharRange$CharacterIterator
-  *  org.apache.commons.lang3.Validate
   */
  package org.apache.commons.lang3;
  
  import java.io.Serializable;
  import java.util.Iterator;
+ import java.util.Objects;
  import org.apache.commons.lang3.CharRange;
- import org.apache.commons.lang3.Validate;
  
  final class CharRange
  implements Iterable<Character>,
  Serializable {
      private static final long serialVersionUID = 8270183163158333422L;
+     static final CharRange[] EMPTY_ARRAY = new CharRange[0];
      private final char start;
      private final char end;
      private final boolean negated;
      private transient String iToString;
-     static final CharRange[] EMPTY_ARRAY = new CharRange[0];
  
-     private CharRange(char start, char end, boolean negated) {
-         if (start > end) {
-             char temp = start;
-             start = end;
-             end = temp;
-         }
-         this.start = start;
-         this.end = end;
-         this.negated = negated;
-     }
- 
      public static CharRange is(char ch) {
          return new CharRange(ch, ch, false);
      }
  
-     public static CharRange isNot(char ch) {
-         return new CharRange(ch, ch, true);
-     }
- 
      public static CharRange isIn(char start, char end) {
          return new CharRange(start, end, false);
      }
  
-     public static CharRange isNotIn(char start, char end) {
-         return new CharRange(start, end, true);
-     }
- 
-     public char getStart() {
-         return this.start;
+     public static CharRange isNot(char ch) {
+         return new CharRange(ch, ch, true);
      }
  
-     public char getEnd() {
-         return this.end;
+     public static CharRange isNotIn(char start, char end) {
+         return new CharRange(start, end, true);
      }
  
-     public boolean isNegated() {
-         return this.negated;
+     private CharRange(char start, char end, boolean negated) {
+         if (start > end) {
+             char temp = start;
+             start = end;
+             end = temp;
+         }
+         this.start = start;
+         this.end = end;
+         this.negated = negated;
      }
  
      public boolean contains(char ch) {
          return (ch >= this.start && ch <= this.end) != this.negated;
      }
  
      public boolean contains(CharRange range) {
-         Validate.notNull((Object)range, (String)"range", (Object[])new Object[0]);
+         Objects.requireNonNull(range, "range");
          if (this.negated) {
              if (range.negated) {
                  return this.start >= range.start && this.end <= range.end;
              }
              return range.end < this.start || range.start > this.end;
          }
          if (range.negated) {
              return this.start == '\u0000' && this.end == '\uffff';
          }
          return this.start <= range.start && this.end >= range.end;
      }
  
      public boolean equals(Object obj) {
          if (obj == this) {
              return true;
          }
          if (!(obj instanceof CharRange)) {
              return false;
          }
          CharRange other = (CharRange)obj;
          return this.start == other.start && this.end == other.end && this.negated == other.negated;
      }
  
+     public char getEnd() {
+         return this.end;
+     }
+ 
+     public char getStart() {
+         return this.start;
+     }
+ 
      public int hashCode() {
          return 83 + this.start + 7 * this.end + (this.negated ? 1 : 0);
      }
  
+     public boolean isNegated() {
+         return this.negated;
+     }
+ 
+     @Override
+     public Iterator<Character> iterator() {
+         return new CharacterIterator(this, null);
+     }
+ 
      public String toString() {
          if (this.iToString == null) {
              StringBuilder buf = new StringBuilder(4);
              if (this.isNegated()) {
                  buf.append('^');
              }
              buf.append(this.start);
              if (this.start != this.end) {
                  buf.append('-');
                  buf.append(this.end);
              }
              this.iToString = buf.toString();
          }
          return this.iToString;
      }
  
-     @Override
-     public Iterator<Character> iterator() {
-         return new CharacterIterator(this, null);
-     }
- 
-     static /* synthetic */ boolean access$100(CharRange x0) {
+     static /* synthetic */ boolean access$000(CharRange x0) {
          return x0.negated;
      }
  
-     static /* synthetic */ char access$200(CharRange x0) {
+     static /* synthetic */ char access$100(CharRange x0) {
          return x0.start;
      }
  
-     static /* synthetic */ char access$300(CharRange x0) {
+     static /* synthetic */ char access$200(CharRange x0) {
          return x0.end;
      }
  }
  

```

### org/apache/commons/lang3/CharSequenceUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.ArrayUtils
   *  org.apache.commons.lang3.StringUtils
   */
  package org.apache.commons.lang3;
  
  import org.apache.commons.lang3.ArrayUtils;
  import org.apache.commons.lang3.StringUtils;
  
  public class CharSequenceUtils {
      private static final int NOT_FOUND = -1;
      static final int TO_STRING_LIMIT = 16;
  
-     public static CharSequence subSequence(CharSequence cs, int start) {
-         return cs == null ? null : cs.subSequence(start, cs.length());
-     }
- 
-     static int indexOf(CharSequence cs, int searchChar, int start) {
-         if (cs instanceof String) {
-             return ((String)cs).indexOf(searchChar, start);
-         }
-         int sz = cs.length();
-         if (start < 0) {
-             start = 0;
-         }
-         if (searchChar < 65536) {
-             for (int i = start; i < sz; ++i) {
-                 if (cs.charAt(i) != searchChar) continue;
-                 return i;
-             }
-             return -1;
-         }
-         if (searchChar <= 0x10FFFF) {
-             char[] chars = Character.toChars(searchChar);
-             for (int i = start; i < sz - 1; ++i) {
-                 char high = cs.charAt(i);
-                 char low = cs.charAt(i + 1);
-                 if (high != chars[0] || low != chars[1]) continue;
-                 return i;
-             }
+     private static boolean checkLaterThan1(CharSequence cs, CharSequence searchChar, int len2, int start1) {
+         int i = 1;
+         for (int j = len2 - 1; i <= j; ++i, --j) {
+             if (cs.charAt(start1 + i) == searchChar.charAt(i) && cs.charAt(start1 + j) == searchChar.charAt(j)) continue;
+             return false;
          }
-         return -1;
+         return true;
      }
  
      static int indexOf(CharSequence cs, CharSequence searchChar, int start) {
          if (cs instanceof String) {
              return ((String)cs).indexOf(searchChar.toString(), start);
          }
          if (cs instanceof StringBuilder) {
              return ((StringBuilder)cs).indexOf(searchChar.toString(), start);
          }
          if (cs instanceof StringBuffer) {
              return ((StringBuffer)cs).indexOf(searchChar.toString(), start);
          }
          return cs.toString().indexOf(searchChar.toString(), start);
      }
  
-     static int lastIndexOf(CharSequence cs, int searchChar, int start) {
+     static int indexOf(CharSequence cs, int searchChar, int start) {
          if (cs instanceof String) {
-             return ((String)cs).lastIndexOf(searchChar, start);
+             return ((String)cs).indexOf(searchChar, start);
          }
          int sz = cs.length();
          if (start < 0) {
-             return -1;
-         }
-         if (start >= sz) {
-             start = sz - 1;
+             start = 0;
          }
          if (searchChar < 65536) {
-             for (int i = start; i >= 0; --i) {
+             for (int i = start; i < sz; ++i) {
                  if (cs.charAt(i) != searchChar) continue;
                  return i;
              }
              return -1;
          }
          if (searchChar <= 0x10FFFF) {
              char[] chars = Character.toChars(searchChar);
-             if (start == sz - 1) {
-                 return -1;
-             }
-             for (int i = start; i >= 0; --i) {
+             for (int i = start; i < sz - 1; ++i) {
                  char high = cs.charAt(i);
                  char low = cs.charAt(i + 1);
-                 if (chars[0] != high || chars[1] != low) continue;
+                 if (high != chars[0] || low != chars[1]) continue;
                  return i;
              }
          }
          return -1;
      }
  
      /*
       * Unable to fully structure code
       */
      static int lastIndexOf(CharSequence cs, CharSequence searchChar, int start) {
          if (searchChar == null || cs == null) {
              return -1;
          }
          if (searchChar instanceof String) {
              if (cs instanceof String) {
                  return ((String)cs).lastIndexOf((String)searchChar, start);
              }
              if (cs instanceof StringBuilder) {
                  return ((StringBuilder)cs).lastIndexOf((String)searchChar, start);
              }
              if (cs instanceof StringBuffer) {
                  return ((StringBuffer)cs).lastIndexOf((String)searchChar, start);
              }
          }
          len1 = cs.length();
          len2 = searchChar.length();
          if (start > len1) {
              start = len1;
          }
-         if (start < 0 || len2 < 0 || len2 > len1) {
+         if (start < 0 || len2 > len1) {
              return -1;
          }
          if (len2 == 0) {
              return start;
          }
          if (len2 <= 16) {
              if (cs instanceof String) {
                  return ((String)cs).lastIndexOf(searchChar.toString(), start);
              }
              if (cs instanceof StringBuilder) {
                  return ((StringBuilder)cs).lastIndexOf(searchChar.toString(), start);
              }
              if (cs instanceof StringBuffer) {
                  return ((StringBuffer)cs).lastIndexOf(searchChar.toString(), start);
              }
          }
          if (start + len2 > len1) {
              start = len1 - len2;
          }
          char0 = searchChar.charAt(0);
          i = start;
          do lbl-1000:
          // 3 sources
  
          {
              block14: {
                  if (cs.charAt(i) == char0) break block14;
                  if (--i >= 0) ** GOTO lbl-1000
                  return -1;
              }
              if (!CharSequenceUtils.checkLaterThan1(cs, searchChar, len2, i)) continue;
              return i;
          } while (--i >= 0);
          return -1;
      }
  
-     private static boolean checkLaterThan1(CharSequence cs, CharSequence searchChar, int len2, int start1) {
-         int i = 1;
-         for (int j = len2 - 1; i <= j; ++i, --j) {
-             if (cs.charAt(start1 + i) == searchChar.charAt(i) && cs.charAt(start1 + j) == searchChar.charAt(j)) continue;
-             return false;
+     static int lastIndexOf(CharSequence cs, int searchChar, int start) {
+         if (cs instanceof String) {
+             return ((String)cs).lastIndexOf(searchChar, start);
          }
-         return true;
-     }
- 
-     public static char[] toCharArray(CharSequence source) {
-         int len = StringUtils.length((CharSequence)source);
-         if (len == 0) {
-             return ArrayUtils.EMPTY_CHAR_ARRAY;
+         int sz = cs.length();
+         if (start < 0) {
+             return -1;
          }
-         if (source instanceof String) {
-             return ((String)source).toCharArray();
+         if (start >= sz) {
+             start = sz - 1;
          }
-         char[] array = new char[len];
-         for (int i = 0; i < len; ++i) {
-             array[i] = source.charAt(i);
+         if (searchChar < 65536) {
+             for (int i = start; i >= 0; --i) {
+                 if (cs.charAt(i) != searchChar) continue;
+                 return i;
+             }
+             return -1;
          }
-         return array;
+         if (searchChar <= 0x10FFFF) {
+             char[] chars = Character.toChars(searchChar);
+             if (start == sz - 1) {
+                 return -1;
+             }
+             for (int i = start; i >= 0; --i) {
+                 char high = cs.charAt(i);
+                 char low = cs.charAt(i + 1);
+                 if (chars[0] != high || chars[1] != low) continue;
+                 return i;
+             }
+         }
+         return -1;
      }
  
      static boolean regionMatches(CharSequence cs, boolean ignoreCase, int thisStart, CharSequence substring, int start, int length) {
          if (cs instanceof String && substring instanceof String) {
              return ((String)cs).regionMatches(ignoreCase, thisStart, (String)substring, start, length);
          }
          int index1 = thisStart;
          int index2 = start;
          int tmpLen = length;
          int srcLen = cs.length() - thisStart;
          int otherLen = substring.length() - start;
          if (thisStart < 0 || start < 0 || length < 0) {
              return false;
          }
          if (srcLen < length || otherLen < length) {
              return false;
          }
          while (tmpLen-- > 0) {
              char u2;
              char c2;
              char c1;
              if ((c1 = cs.charAt(index1++)) == (c2 = substring.charAt(index2++))) continue;
              if (!ignoreCase) {
                  return false;
              }
              char u1 = Character.toUpperCase(c1);
              if (u1 == (u2 = Character.toUpperCase(c2)) || Character.toLowerCase(u1) == Character.toLowerCase(u2)) continue;
              return false;
          }
          return true;
      }
+ 
+     public static CharSequence subSequence(CharSequence cs, int start) {
+         return cs == null ? null : cs.subSequence(start, cs.length());
+     }
+ 
+     public static char[] toCharArray(CharSequence source) {
+         int len = StringUtils.length((CharSequence)source);
+         if (len == 0) {
+             return ArrayUtils.EMPTY_CHAR_ARRAY;
+         }
+         if (source instanceof String) {
+             return ((String)source).toCharArray();
+         }
+         char[] array = new char[len];
+         for (int i = 0; i < len; ++i) {
+             array[i] = source.charAt(i);
+         }
+         return array;
+     }
  }
  

```

### org/apache/commons/lang3/CharSet.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.CharRange
   */
  package org.apache.commons.lang3;
  
  import java.io.Serializable;
  import java.util.Collections;
  import java.util.HashMap;
  import java.util.HashSet;
  import java.util.Map;
  import java.util.Set;
+ import java.util.stream.Stream;
  import org.apache.commons.lang3.CharRange;
  
  public class CharSet
  implements Serializable {
      private static final long serialVersionUID = 5947847346149275958L;
      public static final CharSet EMPTY = new CharSet(new String[]{null});
      public static final CharSet ASCII_ALPHA = new CharSet("a-zA-Z");
      public static final CharSet ASCII_ALPHA_LOWER = new CharSet("a-z");
      public static final CharSet ASCII_ALPHA_UPPER = new CharSet("A-Z");
      public static final CharSet ASCII_NUMERIC = new CharSet("0-9");
      protected static final Map<String, CharSet> COMMON = Collections.synchronizedMap(new HashMap());
      private final Set<CharRange> set = Collections.synchronizedSet(new HashSet());
  
      public static CharSet getInstance(String ... setStrs) {
          CharSet common;
          if (setStrs == null) {
              return null;
          }
          if (setStrs.length == 1 && (common = COMMON.get(setStrs[0])) != null) {
              return common;
          }
          return new CharSet(setStrs);
      }
  
      protected CharSet(String ... set) {
-         for (String s : set) {
-             this.add(s);
-         }
+         Stream.of(set).forEach(this::add);
      }
  
      protected void add(String str) {
          if (str == null) {
              return;
          }
          int len = str.length();
          int pos = 0;
          while (pos < len) {
              int remainder = len - pos;
              if (remainder >= 4 && str.charAt(pos) == '^' && str.charAt(pos + 2) == '-') {
                  this.set.add(CharRange.isNotIn((char)str.charAt(pos + 1), (char)str.charAt(pos + 3)));
                  pos += 4;
                  continue;
              }
              if (remainder >= 3 && str.charAt(pos + 1) == '-') {
                  this.set.add(CharRange.isIn((char)str.charAt(pos), (char)str.charAt(pos + 2)));
                  pos += 3;
                  continue;
              }
              if (remainder >= 2 && str.charAt(pos) == '^') {
                  this.set.add(CharRange.isNot((char)str.charAt(pos + 1)));
                  pos += 2;
                  continue;
              }
              this.set.add(CharRange.is((char)str.charAt(pos)));
              ++pos;
          }
      }
  
-     CharRange[] getCharRanges() {
-         return this.set.toArray(CharRange.EMPTY_ARRAY);
-     }
- 
      /*
       * WARNING - Removed try catching itself - possible behaviour change.
       */
      public boolean contains(char ch) {
          Set<CharRange> set = this.set;
          synchronized (set) {
-             for (CharRange range : this.set) {
-                 if (!range.contains(ch)) continue;
-                 return true;
-             }
+             return this.set.stream().anyMatch(range -> range.contains(ch));
          }
-         return false;
      }
  
      public boolean equals(Object obj) {
          if (obj == this) {
              return true;
          }
          if (!(obj instanceof CharSet)) {
              return false;
          }
          CharSet other = (CharSet)obj;
          return this.set.equals(other.set);
      }
  
+     CharRange[] getCharRanges() {
+         return this.set.toArray(CharRange.EMPTY_ARRAY);
+     }
+ 
      public int hashCode() {
          return 89 + this.set.hashCode();
      }
  
      public String toString() {
          return this.set.toString();
      }
  
      static {
          COMMON.put(null, EMPTY);
          COMMON.put("", EMPTY);
          COMMON.put("a-zA-Z", ASCII_ALPHA);
          COMMON.put("A-Za-z", ASCII_ALPHA);
          COMMON.put("a-z", ASCII_ALPHA_LOWER);
          COMMON.put("A-Z", ASCII_ALPHA_UPPER);
          COMMON.put("0-9", ASCII_NUMERIC);
      }
  }
  

```

### org/apache/commons/lang3/CharSetUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
   *  org.apache.commons.lang3.CharSet
   *  org.apache.commons.lang3.StringUtils
+  *  org.apache.commons.lang3.stream.Streams
   */
  package org.apache.commons.lang3;
  
  import org.apache.commons.lang3.CharSet;
  import org.apache.commons.lang3.StringUtils;
+ import org.apache.commons.lang3.stream.Streams;
  
  public class CharSetUtils {
      public static boolean containsAny(String str, String ... set) {
          if (StringUtils.isEmpty((CharSequence)str) || CharSetUtils.deepEmpty(set)) {
              return false;
          }
          CharSet chars = CharSet.getInstance((String[])set);
          for (char c : str.toCharArray()) {
              if (!chars.contains(c)) continue;
              return true;
          }
          return false;
      }
  
      public static int count(String str, String ... set) {
          if (StringUtils.isEmpty((CharSequence)str) || CharSetUtils.deepEmpty(set)) {
              return 0;
          }
          CharSet chars = CharSet.getInstance((String[])set);
          int count = 0;
          for (char c : str.toCharArray()) {
              if (!chars.contains(c)) continue;
              ++count;
          }
          return count;
      }
  
      private static boolean deepEmpty(String[] strings) {
-         if (strings != null) {
-             for (String s : strings) {
-                 if (!StringUtils.isNotEmpty((CharSequence)s)) continue;
-                 return false;
-             }
-         }
-         return true;
+         return Streams.of((Object[])strings).allMatch(StringUtils::isEmpty);
      }
  
      public static String delete(String str, String ... set) {
          if (StringUtils.isEmpty((CharSequence)str) || CharSetUtils.deepEmpty(set)) {
              return str;
          }
          return CharSetUtils.modify(str, set, false);
      }
  
      public static String keep(String str, String ... set) {
          if (str == null) {
              return null;
          }
          if (str.isEmpty() || CharSetUtils.deepEmpty(set)) {
              return "";
          }
          return CharSetUtils.modify(str, set, true);
      }
  
      private static String modify(String str, String[] set, boolean expect) {
          char[] chrs;
          CharSet chars = CharSet.getInstance((String[])set);
          StringBuilder buffer = new StringBuilder(str.length());
          for (char chr : chrs = str.toCharArray()) {
              if (chars.contains(chr) != expect) continue;
              buffer.append(chr);
          }
          return buffer.toString();
      }
  
      public static String squeeze(String str, String ... set) {
          if (StringUtils.isEmpty((CharSequence)str) || CharSetUtils.deepEmpty(set)) {
              return str;
          }
          CharSet chars = CharSet.getInstance((String[])set);
          StringBuilder buffer = new StringBuilder(str.length());
          char[] chrs = str.toCharArray();
          int sz = chrs.length;
          char lastChar = chrs[0];
-         char ch = ' ';
          Character inChars = null;
          Character notInChars = null;
          buffer.append(lastChar);
          for (int i = 1; i < sz; ++i) {
-             ch = chrs[i];
+             char ch = chrs[i];
              if (ch == lastChar) {
                  if (inChars != null && ch == inChars.charValue()) continue;
                  if (notInChars == null || ch != notInChars.charValue()) {
                      if (chars.contains(ch)) {
                          inChars = Character.valueOf(ch);
                          continue;
                      }
                      notInChars = Character.valueOf(ch);
                  }
              }
              buffer.append(ch);
              lastChar = ch;
          }
          return buffer.toString();
      }
  }
  

```

### org/apache/commons/lang3/CharUtils.class
- 反编译引擎：cfr
```diff
  /*
   * Decompiled with CFR 0.152.
   * 
   * Could not load the following classes:
+  *  org.apache.commons.lang3.ArrayUtils
   *  org.apache.commons.lang3.StringUtils
   *  org.apache.commons.lang3.Validate
   */
  package org.apache.commons.lang3;
  
+ import java.util.Objects;
+ import org.apache.commons.lang3.ArrayUtils;
  import org.apache.commons.lang3.StringUtils;
  import org.apache.commons.lang3.Validate;
  
  public class CharUtils {
      private static final String[] CHAR_STRING_ARRAY = new String[128];
      private static final char[] HEX_DIGITS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
      public static final char LF = '\n';
      public static final char CR = '\r';
      public static final char NUL = '\u0000';
  
-     @Deprecated
-     public static Character toCharacterObject(char ch) {
-         return Character.valueOf(ch);
+     public static int compare(char x, char y) {
+         return x - y;
      }
  
-     public static Character toCharacterObject(String str) {
-         if (StringUtils.isEmpty((CharSequence)str)) {
-             return null;
-         }
-         return Character.valueOf(str.charAt(0));
+     public static boolean isAscii(char ch) {
+         return ch < '?';
      }
  
+     public static boolean isAsciiAlpha(char ch) {
+         return CharUtils.isAsciiAlphaUpper(ch) || CharUtils.isAsciiAlphaLower(ch);
+     }
+ 
+     public static boolean isAsciiAlphaLower(char ch) {
+         return ch >= 'a' && ch <= 'z';
+     }
+ 
+     public static boolean isAsciiAlphanumeric(char ch) {
+         return CharUtils.isAsciiAlpha(ch) || CharUtils.isAsciiNumeric(ch);
+     }
+ 
+     public static boolean isAsciiAlphaUpper(char ch) {
+         return ch >= 'A' && ch <= 'Z';
+     }
+ 
+     public static boolean isAsciiControl(char ch) {
+         return ch < ' ' || ch == '';
+     }
+ 
+     public static boolean isAsciiNumeric(char ch) {
+         return ch >= '0' && ch <= '9';
+     }
+ 
+     public static boolean isAsciiPrintable(char ch) {
+         return ch >= ' ' && ch < '';
+     }
+ 
      public static char toChar(Character ch) {
-         Validate.notNull((Object)ch, (String)"ch", (Object[])new Object[0]);
-         return ch.charValue();
+         return Objects.requireNonNull(ch, "ch").charValue();
      }
  
      public static char toChar(Character ch, char defaultValue) {
-         if (ch == null) {
-             return defaultValue;
-         }
-         return ch.charValue();
+         return ch != null ? ch.charValue() : defaultValue;
      }
  
      public static char toChar(String str) {
          Validate.notEmpty((CharSequence)str, (String)"The String must not be empty", (Object[])new Object[0]);
          return str.charAt(0);
      }
  
      public static char toChar(String str, char defaultValue) {
-         if (StringUtils.isEmpty((CharSequence)str)) {
-             return defaultValue;
-         }
-         return str.charAt(0);
+         return StringUtils.isEmpty((CharSequence)str) ? defaultValue : str.charAt(0);
      }
  
+     @Deprecated
+     public static Character toCharacterObject(char ch) {
+         return Character.valueOf(ch);
+     }
+ 
+     public static Character toCharacterObject(String str) {
+         return StringUtils.isEmpty((CharSequence)str) ? null : Character.valueOf(str.charAt(0));
+     }
+ 
      public static int toIntValue(char ch) {
          if (!CharUtils.isAsciiNumeric(ch)) {
              throw new IllegalArgumentException("The character " + ch + " is not in the range '0' - '9'");
          }
          return ch - 48;
      }
  
      public static int toIntValue(char ch, int defaultValue) {
-         if (!CharUtils.isAsciiNumeric(ch)) {
-             return defaultValue;
-         }
-         return ch - 48;
+         return CharUtils.isAsciiNumeric(ch) ? ch - 48 : defaultValue;
      }
  
      public static int toIntValue(Character ch) {
-         Validate.notNull((Object)ch, (String)"ch", (Object[])new Object[0]);
-         return CharUtils.toIntValue(ch.charValue());
+         return CharUtils.toIntValue(CharUtils.toChar(ch));
      }
  
      public static int toIntValue(Character ch, int defaultValue) {
-         if (ch == null) {
-             return defaultValue;
-         }
-         return CharUtils.toIntValue(ch.charValue(), defaultValue);
+         return ch != null ? CharUtils.toIntValue(ch.charValue(), defaultValue) : defaultValue;
      }
  
      public static String toString(char ch) {
-         if (ch < '?') {
+         if (ch < CHAR_STRING_ARRAY.length) {
              return CHAR_STRING_ARRAY[ch];
          }
-         return new String(new char[]{ch});
+         return String.valueOf(ch);
      }
  
      public static String toString(Character ch) {
-         if (ch == null) {
-             return null;
-         }
-         return CharUtils.toString(ch.charValue());
+         return ch != null ? CharUtils.toString(ch.charValue()) : null;
      }
  
      public static String unicodeEscaped(char ch) {
          return "\\u" + HEX_DIGITS[ch >> 12 & 0xF] + HEX_DIGITS[ch >> 8 & 0xF] + HEX_DIGITS[ch >> 4 & 0xF] + HEX_DIGITS[ch & 0xF];
      }
  
      public static String unicodeEscaped(Character ch) {
-         if (ch == null) {
-             return null;
-         }
-         return CharUtils.unicodeEscaped(ch.charValue());
-     }
- 
-     public static boolean isAscii(char ch) {
-         return ch < '?';
-     }
- 
-     public static boolean isAsciiPrintable(char ch) {
-         return ch >= ' ' && ch < '';
-     }
- 
-     public static boolean isAsciiControl(char ch) {
-         return ch < ' ' || ch == '';
-     }
- 
-     public static boolean isAsciiAlpha(char ch) {
-         return CharUtils.isAsciiAlphaUpper(ch) || CharUtils.isAsciiAlphaLower(ch);
-     }
- 
-     public static boolean isAsciiAlphaUpper(char ch) {
-         return ch >= 'A' && ch <= 'Z';
-     }
- 
-     public static boolean isAsciiAlphaLower(char ch) {
-         return ch >= 'a' && ch <= 'z';
-     }
- 
-     public static boolean isAsciiNumeric(char ch) {
-         return ch >= '0' && ch <= '9';
-     }
- 
-     public static boolean isAsciiAlphanumeric(char ch) {
-         return CharUtils.isAsciiAlpha(ch) || CharUtils.isAsciiNumeric(ch);
-     }
- 
-     public static int compare(char x, char y) {
-         return x - y;
+         return ch != null ? CharUtils.unicodeEscaped(ch.charValue()) : null;
      }
  
      static {
-         for (char c = '\u0000'; c < CHAR_STRING_ARRAY.length; c = (char)(c + '\u0001')) {
-             CharUtils.CHAR_STRING_ARRAY[c] = String.valueOf(c);
-         }
+         ArrayUtils.setAll((Object[])CHAR_STRING_ARRAY, i -> String.valueOf((char)i));
      }
  }
  

```

## 四、破坏性变更清单（删除类）
- `org/apache/commons/lang3/time/FormatCache$ArrayKey.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/time/FormatCache.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/tuple/Pair$PairAdapter.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/tuple/Triple$TripleAdapter.class`（潜在对外 API / 行为移除，需重点回归）

## 五、审计摘要（基础）
- 比对时间：2026-08-08 09:01:18
- 老包标识：3.12.0
- 新包标识：3.14.0
- AI 分析：未接入（core.ai 待补），本报告不含 AI 章节。
