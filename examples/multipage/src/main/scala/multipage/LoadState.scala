package multipage

enum LoadState[+A] {
  case Idle
  case Loading
  case Loaded(value: A)
  case Failed(error: Throwable)
}
