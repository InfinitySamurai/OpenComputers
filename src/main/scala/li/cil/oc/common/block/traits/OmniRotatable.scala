package li.cil.oc.common.block.traits

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import net.minecraft.block.Block
import net.minecraft.block.properties.IProperty
import net.minecraft.block.properties.PropertyDirection
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.property.IUnlistedProperty

import scala.collection.mutable

// Provides 2-axis rotation for blocks (pitch and yaw) using metadata to store the rotation.
trait OmniRotatable extends Block with Extended {
  def getPitch(state: IBlockState) = state.getValue(OmniRotatable.Pitch).asInstanceOf[EnumFacing]

  def getYaw(state: IBlockState) = state.getValue(OmniRotatable.Yaw).asInstanceOf[EnumFacing]

  def withPitchAndYaw(state: IBlockState, pitch: EnumFacing, yaw: EnumFacing) = state.
    withProperty(OmniRotatable.Pitch, pitch).
    withProperty(OmniRotatable.Yaw, yaw)

  override protected def createProperties(listed: mutable.ArrayBuffer[IProperty], unlisted: mutable.ArrayBuffer[IUnlistedProperty[_]]): Unit = {
    super.createProperties(listed, unlisted)
    listed += OmniRotatable.Pitch
    listed += OmniRotatable.Yaw
  }
}

object OmniRotatable {
  final val Pitch = PropertyDirection.create("pitch", Predicates.instanceOf(classOf[EnumFacing]))
  final val Yaw = PropertyDirection.create("yaw", EnumFacing.Plane.HORIZONTAL.asInstanceOf[Predicate[EnumFacing]])
}