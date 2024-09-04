package hellacrash

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tile.{LazyRoCC, LazyRoCCImp}

class OutstandingRequestEntry extends Bundle {
  val isBusy = Bool()
  val value = UInt(64.W)
}

abstract class HellaCrashAccel(opcodes: OpcodeSet)(implicit p: Parameters)
  extends LazyRoCC(opcodes=opcodes)
{
  override lazy val module = new HellaCrashAccelImp(this)
}

class HellaCrashAccelImp(outer: HellaCrashAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) 
  with HasCoreParameters
  with HasL1CacheParameters {

  val cmd = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr = cmd.bits.rs2(log2Up(outer.n)-1,0)
  val doWrite = funct === 0.U
  val doRead = funct === 1.U
  val doLoad = funct === 2.U
  val doAccum = funct === 3.U
  val memRespTag = io.mem.resp.bits.tag(log2Up(outer.n)-1,0)

  val outstanding = RegInit(VecInit(outer.n, 0.U.asTypeOf(new OutstandingRequestEntry)))

  val addr = Reg(UInt(48.W))
  val inc = Reg(UInt(16.W))
  val sentCounter = RegInit(0.U(48.W))

  cmd.ready = sentCounter === 0.U && outstanding.forall(p => !p.isBusy)
  when (cmd.fire) {
    addr := cmd.bits.rs1(47,0)
    inc := cmd.bits.rs1(63,48)
    sentCounter := cmd.bits.rs2
  }


  val tag = outstanding.indexWhere(p => !p.isBusy)
  io.mem.req.valid := sentCounter > 0.U
  io.mem.req.bits.cmd := M_XRD
  io.mem.req.bits.addr := addr
  when (io.mem.req.fire) {
    addr := addr + inc
    sentCounter := sentCounter - 1.U
    outstanding(tag).isBusy := true.B
  }

  when (io.mem.resp.valid) {
    outstanding(io.mem.resp.bits.tag).value := io.mem.resp.bits.data
    outstanding(io.mem.resp.bits.tag).isBusy := false.B
  }


  val backpressure = "HellaCrash bp"
  val idle = "HellaCrash idle"
  val sending = "HellaCrash sending"

  val active = outstanding.exists(p => p.isBusy) || sentCounter > 0.U
  when (active) {
    when (io.mem.req.fire) { printf(f"${sending}\n") }
    .elsewhen (io.mem.req.valid) { printf(f"${backpressure}\n") }
    .otherwise { printf(f"${idle}\n") }
  }
} 
