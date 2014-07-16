package li.cil.oc.common.tileentity

import cpw.mods.fml.common.Optional
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.peripheral.{IComputerAccess, IPeripheral}
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.Slot
import li.cil.oc.api.network.{Message, Packet}
import li.cil.oc.common.{InventorySlots, item}
import li.cil.oc.server.PacketSender
import li.cil.oc.util.mods.Mods
import li.cil.oc.{Items, Settings, api}
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.mutable

@Optional.Interface(iface = "dan200.computercraft.api.peripheral.IPeripheral", modid = "ComputerCraft")
class Switch extends traits.Hub with traits.NotAnalyzable with IPeripheral with traits.ComponentInventory {
  var lastMessage = 0L

  val computers = mutable.Buffer.empty[AnyRef]

  val openPorts = mutable.Map.empty[AnyRef, mutable.Set[Int]]

  override def canUpdate = isServer

  // ----------------------------------------------------------------------- //

  @Optional.Method(modid = "ComputerCraft")
  override def getType = "oc_adapter"

  @Optional.Method(modid = "ComputerCraft")
  override def attach(computer: IComputerAccess) {
    computers += computer
    openPorts += computer -> mutable.Set.empty
  }

  @Optional.Method(modid = "ComputerCraft")
  override def detach(computer: IComputerAccess) {
    computers -= computer
    openPorts -= computer
  }

  @Optional.Method(modid = "ComputerCraft")
  override def getMethodNames = Array("open", "isOpen", "close", "closeAll", "maxPacketSize", "transmit", "isWireless")

  @Optional.Method(modid = "ComputerCraft")
  override def callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: Array[AnyRef]) = getMethodNames()(method) match {
    case "open" =>
      val port = checkPort(arguments, 0)
      if (openPorts(computer).size >= 128)
        throw new IllegalArgumentException("too many open channels")
      result(openPorts(computer).add(port))
    case "isOpen" =>
      val port = checkPort(arguments, 0)
      result(openPorts(computer).contains(port))
    case "close" =>
      val port = checkPort(arguments, 0)
      result(openPorts(computer).remove(port))
    case "closeAll" =>
      openPorts(computer).clear()
      null
    case "maxPacketSize" =>
      result(Settings.get.maxNetworkPacketSize)
    case "transmit" =>
      val sendPort = checkPort(arguments, 0)
      val answerPort = checkPort(arguments, 1)
      val data = Seq(Int.box(answerPort)) ++ arguments.drop(2)
      val packet = api.Network.newPacket(s"cc${computer.getID}_${computer.getAttachmentName}", null, sendPort, data.toArray)
      result(tryEnqueuePacket(ForgeDirection.UNKNOWN, packet))
    case "isWireless" => result(this.isInstanceOf[AccessPoint])
    case _ => null
  }

  @Optional.Method(modid = "ComputerCraft")
  override def equals(other: IPeripheral) = other == this

  // ----------------------------------------------------------------------- //

  protected def checkPort(args: Array[AnyRef], index: Int) = {
    if (args.length < index - 1 || !args(index).isInstanceOf[Double])
      throw new IllegalArgumentException("bad argument #%d (number expected)".format(index + 1))
    val port = args(index).asInstanceOf[Double].toInt
    if (port < 1 || port > 0xFFFF)
      throw new IllegalArgumentException("bad argument #%d (number in [1, 65535] expected)".format(index + 1))
    port
  }

  protected def queueMessage(source: String, destination: String, port: Int, answerPort: Int, args: Array[AnyRef]) {
    for (computer <- computers.map(_.asInstanceOf[IComputerAccess])) {
      val address = s"cc${computer.getID}_${computer.getAttachmentName}"
      if (source != address && Option(destination).forall(_ == address) && openPorts(computer).contains(port))
        computer.queueEvent("modem_message", Array(Seq(computer.getAttachmentName, Int.box(port), Int.box(answerPort)) ++ args.map {
          case x: Array[Byte] => new String(x, "UTF-8")
          case x => x
        }: _*))
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def relayPacket(sourceSide: ForgeDirection, packet: Packet) {
    super.relayPacket(sourceSide, packet)
    val now = System.currentTimeMillis()
    if (now - lastMessage >= (relayDelay - 1) * 50) {
      lastMessage = now
      PacketSender.sendSwitchActivity(this)
    }
  }

  override protected def onPlugMessage(plug: Plug, message: Message) {
    super.onPlugMessage(plug, message)
    if (message.name == "network.message" && Mods.ComputerCraft.isAvailable) {
      message.data match {
        case Array(packet: Packet) =>
          packet.data.headOption match {
            case Some(answerPort: java.lang.Double) =>
              queueMessage(packet.source, packet.destination, packet.port, answerPort.toInt, packet.data.drop(1))
            case _ =>
              queueMessage(packet.source, packet.destination, packet.port, -1, packet.data)
          }
        case _ =>
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def onItemAdded(slot: Int, stack: ItemStack) {
    super.onItemAdded(slot, stack)
    updateLimits(slot, stack)
  }

  private def updateLimits(slot: Int, stack: ItemStack) {
    Driver.driverFor(stack) match {
      case driver if driver.slot(stack) == Slot.Processor =>
        relayDelay = math.max(1, relayBaseDelay - ((driver.tier(stack) + 1) * relayDelayPerUpgrade))
      case driver if driver.slot(stack) == Slot.Memory =>
        relayAmount = math.max(1, relayBaseAmount + (Items.multi.subItem(stack) match {
          case Some(ram: item.Memory) => (ram.tier + 1) * relayAmountPerUpgrade
          case _ => (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)
        }))
      case driver if driver.slot(stack) == Slot.HardDiskDrive =>
        maxQueueSize = math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack) {
    super.onItemRemoved(slot, stack)
    Driver.driverFor(stack) match {
      case driver if driver.slot(stack) == Slot.Processor => relayDelay = relayBaseDelay
      case driver if driver.slot(stack) == Slot.Memory => relayAmount = relayBaseAmount
      case driver if driver.slot(stack) == Slot.HardDiskDrive => maxQueueSize = queueBaseSize
    }
  }

  override def getSizeInventory = InventorySlots.switch.length

  override def isItemValidForSlot(slot: Int, stack: ItemStack) =
    Option(Driver.driverFor(stack)).fold(false)(driver => {
      val provided = InventorySlots.switch(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })

  // ----------------------------------------------------------------------- //

  override def readFromNBT(nbt: NBTTagCompound) {
    super.readFromNBT(nbt)
    for (slot <- 0 until items.length) items(slot) collect {
      case stack => updateLimits(slot, stack)
    }
  }
}