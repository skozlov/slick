package scala.slick.lifted

import java.util.UUID
import java.sql.{Blob, Clob, Date, Time, Timestamp}
import scala.slick.SlickException
import scala.slick.ast.Type
import scala.slick.driver.JdbcProfile
import scala.slick.jdbc.{PositionedParameters, PositionedResult}

/**
 * A (usually implicit) TypeMapper object represents a Scala type that can be
 * used as a column type in the database. The actual implementation of the
 * type is deferred to a TypeMapperDelegate which can depend on the driver.
 * 
 * <p>Custom types with a single implementation can implement both traits in
 * one object:</p>
 * <code><pre>
 * implicit object MyTypeMapper
 *     extends TypeMapper[MyType] with TypeMapperDelegate[MyType] {
 *   def apply(p: JdbcProfile) = this
 *   def zero = ...
 *   def sqlType = ...
 *   def setValue(v: Long, p: PositionedParameters) = ...
 *   def setOption(v: Option[Long], p: PositionedParameters) = ...
 *   def nextValue(r: PositionedResult) = ...
 *   def updateValue(v: Long, r: PositionedResult) = ...
 * }
 * </pre></code>
 */
sealed trait TypeMapper[T] extends (JdbcProfile => TypeMapperDelegate[T]) with Type { self =>
  def createOptionTypeMapper: OptionTypeMapper[T] = new OptionTypeMapper[T](self) {
    def apply(profile: JdbcProfile) = self(profile).createOptionTypeMapperDelegate
    def getBaseTypeMapper[U](implicit ev: Option[U] =:= Option[T]): TypeMapper[U] = self.asInstanceOf[TypeMapper[U]]
  }
  def getBaseTypeMapper[U](implicit ev: Option[U] =:= T): TypeMapper[U]
}

object TypeMapper {
  @inline implicit def typeMapperToOptionTypeMapper[T](implicit t: TypeMapper[T]): OptionTypeMapper[T] = t.createOptionTypeMapper

  implicit object BooleanTypeMapper extends BaseTypeMapper[Boolean] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.booleanTypeMapperDelegate
  }

  implicit object BlobTypeMapper extends BaseTypeMapper[Blob] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.blobTypeMapperDelegate
  }

  implicit object ByteTypeMapper extends BaseTypeMapper[Byte] with NumericTypeMapper {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.byteTypeMapperDelegate
  }

  implicit object ByteArrayTypeMapper extends BaseTypeMapper[Array[Byte]] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.byteArrayTypeMapperDelegate
  }

  implicit object ClobTypeMapper extends BaseTypeMapper[Clob] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.clobTypeMapperDelegate
  }

  implicit object DateTypeMapper extends BaseTypeMapper[Date] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.dateTypeMapperDelegate
  }

  implicit object DoubleTypeMapper extends BaseTypeMapper[Double] with NumericTypeMapper {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.doubleTypeMapperDelegate
  }

  implicit object FloatTypeMapper extends BaseTypeMapper[Float] with NumericTypeMapper {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.floatTypeMapperDelegate
  }

  implicit object IntTypeMapper extends BaseTypeMapper[Int] with NumericTypeMapper {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.intTypeMapperDelegate
  }

  implicit object LongTypeMapper extends BaseTypeMapper[Long] with NumericTypeMapper {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.longTypeMapperDelegate
  }

  implicit object ShortTypeMapper extends BaseTypeMapper[Short] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.shortTypeMapperDelegate
  }

  implicit object StringTypeMapper extends BaseTypeMapper[String] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.stringTypeMapperDelegate
  }

  implicit object TimeTypeMapper extends BaseTypeMapper[Time] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.timeTypeMapperDelegate
  }

  implicit object TimestampTypeMapper extends BaseTypeMapper[Timestamp] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.timestampTypeMapperDelegate
  }

  implicit object UnitTypeMapper extends BaseTypeMapper[Unit] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.unitTypeMapperDelegate
  }

  implicit object UUIDTypeMapper extends BaseTypeMapper[UUID] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.uuidTypeMapperDelegate
  }

  implicit object BigDecimalTypeMapper extends BaseTypeMapper[BigDecimal] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.bigDecimalTypeMapperDelegate
  }

  object NullTypeMapper extends BaseTypeMapper[Null] {
    def apply(profile: JdbcProfile) = profile.typeMapperDelegates.nullTypeMapperDelegate
  }
}

trait BaseTypeMapper[T] extends TypeMapper[T] {
  def getBaseTypeMapper[U](implicit ev: Option[U] =:= T) =
    throw new SlickException("A BaseTypeMapper should not have an Option type")
}

abstract class OptionTypeMapper[T](val base: TypeMapper[T]) extends TypeMapper[Option[T]]

/**
 * Adding this marker trait to a TypeMapper makes the type eligible for
 * numeric operators.
 */
trait NumericTypeMapper

trait TypeMapperDelegate[T] { self =>
  /**
   * A zero value for the type. This is used as a default instead of NULL when
   * used as a non-nullable column.
   */
  def zero: T
  /**
   * The constant from java.sql.Types that is used for setting parameters of
   * the type to NULL.
   */
  def sqlType: Int
  /**
   * The default name for the SQL type that is used for column declarations.
   */
  def sqlTypeName: String
  /**
   * Set a parameter of the type.
   */
  def setValue(v: T, p: PositionedParameters): Unit
  /**
   * Set an Option parameter of the type.
   */
  def setOption(v: Option[T], p: PositionedParameters): Unit
  /**
   * Get a result column of the type.
   */
  def nextValue(r: PositionedResult): T
  /**
   * Update a column of the type in a mutable result set.
   */
  def updateValue(v: T, r: PositionedResult): Unit
  def nextValueOrElse(d: =>T, r: PositionedResult) = { val v = nextValue(r); if(r.rs.wasNull) d else v }
  def nextOption(r: PositionedResult): Option[T] = { val v = nextValue(r); if(r.rs.wasNull) None else Some(v) }
  def updateOption(v: Option[T], r: PositionedResult): Unit = v match {
    case Some(s) => updateValue(s, r)
    case None => r.updateNull()
  }
  def valueToSQLLiteral(value: T): String = value.toString
  def nullable = false

  def createOptionTypeMapperDelegate: TypeMapperDelegate[Option[T]] = new TypeMapperDelegate[Option[T]] {
    def zero = None
    def sqlType = self.sqlType
    override def sqlTypeName = self.sqlTypeName
    def setValue(v: Option[T], p: PositionedParameters) = self.setOption(v, p)
    def setOption(v: Option[Option[T]], p: PositionedParameters) = self.setOption(v.getOrElse(None), p)
    def nextValue(r: PositionedResult) = self.nextOption(r)
    def updateValue(v: Option[T], r: PositionedResult) = self.updateOption(v, r)
    override def valueToSQLLiteral(value: Option[T]): String = value.map(self.valueToSQLLiteral).getOrElse("null")
    override def nullable = true
  }
}

object TypeMapperDelegate {
  private[slick] lazy val typeNames = Map() ++
  (for(f <- classOf[java.sql.Types].getFields)
    yield f.get(null).asInstanceOf[Int] -> f.getName)
}

abstract class MappedTypeMapper[T,U](implicit tm: TypeMapper[U]) extends TypeMapper[T] { self =>
  def map(t: T): U
  def comap(u: U): T

  def sqlType: Option[Int] = None
  def sqlTypeName: Option[String] = None
  def valueToSQLLiteral(value: T): Option[String] = None
  def nullable: Option[Boolean] = None

  def apply(profile: JdbcProfile): TypeMapperDelegate[T] = new TypeMapperDelegate[T] {
    val tmd = tm(profile)
    def zero = comap(tmd.zero)
    def sqlType = self.sqlType.getOrElse(tmd.sqlType)
    override def sqlTypeName = self.sqlTypeName.getOrElse(tmd.sqlTypeName)
    def setValue(v: T, p: PositionedParameters) = tmd.setValue(map(v), p)
    def setOption(v: Option[T], p: PositionedParameters) = tmd.setOption(v.map(map _), p)
    def nextValue(r: PositionedResult) = comap(tmd.nextValue(r))
    def updateValue(v: T, r: PositionedResult) = tmd.updateValue(map(v), r)
    override def valueToSQLLiteral(value: T) = self.valueToSQLLiteral(value).getOrElse(tmd.valueToSQLLiteral(map(value)))
    override def nullable = self.nullable.getOrElse(tmd.nullable)
  }
}

object MappedTypeMapper {
  def base[T, U](tmap: T => U, tcomap: U => T)(implicit tm: TypeMapper[U]): BaseTypeMapper[T] =
    new MappedTypeMapper[T, U] with BaseTypeMapper[T] {
      def map(t: T) = tmap(t)
      def comap(u: U) = tcomap(u)
    }
}
