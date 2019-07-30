package zio.shield.flow

import scalafix.v1._
import scala.meta._
import zio.shield.tag._

case object NullabilityInferrer extends FlowInferrer[Tag.Nullable.type] {

  val constNullableSymbols = List(
    "java/io/File.getParent"
  ) // TODO possible can be constructed via Java reflection or bytecode analysis

  def infer(flowCache: FlowCache)(
      symbol: String): TagProp[Tag.Nullable.type] = {
    if (NullabilityInferrer.constNullableSymbols.contains(symbol)) {
      TagProp(Tag.Nullable, cond = true, List(TagProof.GivenProof))
    } else {
      val constPatch = flowCache.trees.get(symbol) match {
        case Some(tree) =>
          tree.collect {
            case l: Lit.Null =>
              Patch.lint(Diagnostic("", "nullable: null usage", l.pos))
          }.asPatch
        case None => Patch.empty
      }

      val nullableSymbols = flowCache.edges.get(symbol) match {
        case Some(FunctionEdge(_, _, innerSymbols)) =>
          innerSymbols.filter(
            flowCache.searchTag(Tag.Nullable)(_).getOrElse(false))
        case Some(ValVarEdge(innerSymbols)) =>
          innerSymbols.filter(
            flowCache.searchTag(Tag.Nullable)(_).getOrElse(false))
        case _ => List.empty
      }

      val proofs = List(
        TagProof.PatchProof.fromPatch(constPatch),
        TagProof.SymbolsProof.fromSymbols(nullableSymbols)
      ).flatten

      if (proofs.nonEmpty) TagProp(Tag.Nullable, cond = true, proofs)
      else TagProp(Tag.Nullable, cond = false, List(TagProof.ContraryProof))
    }
  }
}
