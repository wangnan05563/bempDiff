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

## 三、反编译源码级差异（Top-3 修改/新增/删除 Java 类）
> 共 3 个类已反编译（本处仅展示前 Top-K 条完整 diff）。

### org/apache/commons/lang3/AnnotationUtils$1.class
- 反编译引擎：cfr(in-process)
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
- 反编译引擎：cfr(in-process)
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
                  Object v1 = m.invoke((Object)a1, new Object[0]);
                  Object v2 = m.invoke((Object)a2, new Object[0]);
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
                  Object value = m.invoke((Object)a, new Object[0]);
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
-                 builder.append(m.getName(), m.invoke((Object)a, new Object[0]));
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
+                 builder.append(m.getName(), m.invoke((Object)a, new Object[0]));
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
- 反编译引擎：cfr(in-process)
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

## 四、前端资源代码差异（Top-3 修改/新增/删除 JS/HTML/CSS）
- 无前端源码（JS/HTML/CSS）变动。

## 五、破坏性变更清单（删除类/删除前端资源）
- `org/apache/commons/lang3/time/FormatCache$ArrayKey.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/time/FormatCache.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/tuple/Pair$PairAdapter.class`（潜在对外 API / 行为移除，需重点回归）
- `org/apache/commons/lang3/tuple/Triple$TripleAdapter.class`（潜在对外 API / 行为移除，需重点回归）

## 六、审计摘要（基础）
- 比对时间：2026-08-12 22:46:01
- 老包标识：3.12.0
- 新包标识：3.14.0
- AI 分析：未接入，本报告不含 AI 章节。
