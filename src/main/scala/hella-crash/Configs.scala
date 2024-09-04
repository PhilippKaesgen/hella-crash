package hellacrash

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Config, Parameters, Field}
import freechips.rocketchip.tile.{BuildRoCC, OpcodeSet}
import freechips.rocketchip.diplomacy.{LazyModule}


class WithHellaCrash extends Config ((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq(
    (p: Parameters) => {
      val acc = LazyModule(new HellaCrashAccel(OpcodeSet.custom1)(p))
      acc
    }
  )
})
