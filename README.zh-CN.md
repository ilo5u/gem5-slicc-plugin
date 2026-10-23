# 面向 CLion / IntelliJ IDEA 的 SLICC / gem5 Ruby 语言插件

[English](README.md) | [简体中文](README.zh-CN.md)

[![许可证](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

为 [gem5](https://www.gem5.org) Ruby 内存子系统缓存一致性框架中使用的
`.sm` 和 `.slicc` 文件提供语言支持。

版本 **1.6.0** — **2026-10-23**。发布说明见 [CHANGELOG.md](CHANGELOG.md)。

## 使用方法

1. **在 CLion 中安装插件。** 按照[安装说明](#在-clion--intellij-idea-中安装)操作，打开项目后等待索引完成。
2. **体验仓库附带的 gem5 Ruby 源码。** 本仓库的 [`ruby/`](ruby/) 文件夹包含 gem5 Ruby 的主要代码，包括 SLICC 协议和相关 C++ 实现。在 CLion 中使用 **File → Open** 将此文件夹作为项目打开。打开 [`MI_example-cache.sm`](ruby/protocol/MI_example-cache.sm) 等协议文件，即可体验高亮、补全、行内提示，以及 `Ctrl+Click` / `Ctrl+B` 导航。
3. **开发 gem5 时，请打开完整的 gem5 仓库根目录。** 建议将 gem5 根目录作为 CLion 项目打开，而不是只打开 `src/mem/ruby` 或某个协议目录。这样可以同时索引 C++ 源码、`.sm` / `.slicc` 文件以及 Ruby 子树之外的头文件，并支持跨文件导航。仓库附带的 `ruby/` 文件夹适合快速体验；若要解析该副本子树之外的依赖，需要完整的 gem5 源码仓库。
4. **查看说明与诊断。** 将鼠标悬停在声明或已解析的使用位置，或者按 `Ctrl+Q`，即可查看其 `desc`。在 action 中输入 `DPRINTF(`，可体验项目范围的 debug flag 补全；字面量格式字符串的参数数量不匹配会产生警告，能够确定的 flag、transition 和函数名称问题仍会报错。

### 更喜欢 VS Code？

如果你更喜欢在 **Visual Studio Code** 中开发 gem5 SLICC 协议，可以使用配套的
[gem5-slicc-plugin-for-vscode](https://github.com/ilo5u/gem5-slicc-plugin-for-vscode)
扩展。本仓库提供 CLion / IntelliJ IDEA 插件；VS Code 扩展的安装与使用方法请参阅
其独立仓库。

## 文档

插件市场介绍、功能列表和 1.6.0 更新内容见 [MARKETPLACE.html](MARKETPLACE.html)。
独立的 Markdown 版介绍见 [DESCRIPTION.md](DESCRIPTION.md)。
HTML 入门指南见 [GET_STARTED.html](GET_STARTED.html)。

产品介绍和 1.6.0 发布说明也同步在
`src/main/resources/META-INF/plugin.xml` 中，分别对应 `<description>` 和 `<change-notes>`。
修改插件市场 HTML 时，请保持这些副本一致。插件市场通常使用上传插件中的元数据；
如果此前启用了永久的页面级介绍覆盖，请调整该设置，重新使用 `plugin.xml`。
详见[官方上架指南](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-description)。

---

## 已实现功能

| 功能 | 说明 |
|---------|--------|
| **语法高亮** | 声明关键字（`machine`、`structure`、`action`、`transition` 等）、控制流、类型关键字、SLICC 内置语句（`peek`、`enqueue`、`trigger`、`stall_and_wait`、`new` 等）、属性关键字、注释、字符串和数字 |
| **语义高亮** | machine / structure / enumeration 名称使用类名高亮；action / function 名称使用函数声明高亮；枚举成员使用常量高亮；结构体字段使用实例字段高亮；隐式变量（`tbe`、`cache_entry`、`address`）使用预定义符号高亮 |
| **文件与符号图标** | 使用圆角方形 `SM` 和 `H` 图标区分 `.sm` 与 `.slicc` 文件；带字母的圆形图标区分 action、枚举、函数、machine、结构体和 transition。浅色与深色主题图标均采用不依赖系统字体的轮廓字形 |
| **附加属性与内置函数** | 像 `main` / `desc` 一样高亮和补全 `block_on`、`format`、`wake_up`、`rsc_stall_handler`、`prot_stall_handler`、`return_by_ref` 和 `return_by_pointer`；解析、高亮和补全 `DPRINTF`、`DPRINTFN`、`APPEND_TRANSITION_COMMENT` 和 `stallPort`，不改变包含 30 个词的保留字列表 |
| **Debug flag 与格式参数数量** | 从 gem5/项目中所有 `SConscript` 文件的字面量 `DebugFlag` / `CompoundFlag` 声明中补全 `DPRINTF` 的第一个参数；缺少 flag、能够确定为未知的 flag，以及缺少格式字符串均报错。对字面量 `DPRINTF` / `DPRINTFN` 的参数数量不匹配给出警告，支持 `%%`、星号宽度/精度和嵌套调用，不检查参数类型 |
| **Transition 与调用诊断** | 报告不存在的 action、按 include 顺序后出现的重复 state/event transition 组合，以及当前可见作用域内不存在的自由函数/限定作用域函数名。保持 machine 隔离，不把重载参数不匹配误认为函数不存在 |
| **说明文档** | 在声明或已解析的使用位置悬停，或按 `Ctrl+Q`，可显示其 `desc`；支持结构体、字段、枚举/常量、函数和 action；相匹配的函数声明与定义可以共享说明 |
| **跳转到声明** | 在标识符上按 `Ctrl+Click`（或 `Ctrl+B`）跳转到其定义，支持块作用域变量、参数/局部变量遮蔽和基于重载的函数解析 |
| **Action 隐式变量导航** | 对隐式 `cache_entry` 使用 `Ctrl+Click` / `Ctrl+B`，跳转到 machine 中主要的 `AbstractCacheEntry` 实现（如 `Entry` 或 CHI 的 `CacheEntry`）；`main="false"` 排除非默认条目，省略 `main` 时默认为 true。隐式 `tbe` 跳转到 `TBE`。显式参数和局部变量优先；类型缺失或存在歧义时，不会选择无关声明 |
| **外部 SLICC 类型 → C++ 定义** | 在标记为 `external="yes"` 的 `structure` 名称或 `external_type` 声明名称上使用 `Ctrl+Click` / `Ctrl+B`，打开可达 gem5 C++ 类型定义或支持的别名。machine 所属的非限定名称优先匹配 `gem5::ruby::<protocol>::Name`，然后匹配 `gem5::ruby::Name`，不会搜索其他协议。保持限定 `external_name` 的精确匹配，沿字面量 `Source` / `MakeInclude` 条目和传递 include 查找，并隔离无关的 Ruby 根目录。定义缺失时报错；源码上下文不可用时给出警告 |
| **Action → transitions** | 在 action 声明名上使用 `Ctrl+Click` / `Ctrl+B`，如果仅有一处引用则直接跳转；如果有多个匹配，则显示包含 transition 头部与文件的选择列表 |
| **machine / 结构体字段和枚举常量 → 使用位置** | 在 machine 字段、结构体字段或枚举常量声明上使用 `Ctrl+Click` / `Ctrl+B`，查找实际使用语句，包括读取、写入、初始化，以及 transition 中的 state/event 引用。machine 字段包含同一 machine 的 include 闭包内函数/action 中的引用。按解析后的声明匹配，遵循接收者类型、继承、遮蔽和 machine/include 归属关系，并按语句去重；只有一条语句时直接跳转，多条时显示带文件/行号上下文的选择列表 |
| **枚举默认值导航** | 将非字符串形式的 `EnumName_MemberName` 默认值解析到可见枚举成员，遵循声明的枚举类型、归属关系和无歧义的下划线匹配 |
| **自动生成的枚举/状态辅助函数** | 识别全局 `EnumName_to_string`、machine 局部的 `MachineName_EnumName_to_string`，以及 `L1Cache_State_to_permission` 等状态辅助函数。遵循 gem5 生成的类型标识符，包括 external/primitive 类型名称或 `external_name`；提供函数调用高亮和声明导航，同时保留显式函数/变量及作用域的优先级 |
| **函数导航** | 调用和独立声明跳转到匹配的定义；在定义名或不存在 SLICC 定义的声明名（包括 structure/external-type 方法）上点击时，列出函数/action 中的具体调用语句，并跳转到调用所在行。一条语句时直接跳转；多条时显示带文件/行号的选择列表。多行语句压缩为单行展示，默认最多 160 个字符并带省略号；可通过 IDE VM 选项 `-Dslicc.navigation.maxStatementLength=240` 调整。按参数签名和 machine/include/type 作用域匹配；仅有声明的函数调用仍跳转到其声明 |
| **跳转到符号** | 使用 `Ctrl+Alt+Shift+N`，按名称搜索 action、函数、machine 或类型 |
| **查找使用位置** | 在具名声明上使用 `Alt+F7` 查找引用位置，区分同名字段、局部变量和已解析的函数重载 |
| **自动补全** | 补全所有 SLICC 关键字、gem5 内置类型、文件内符号，以及通过 include、协议文件和全局声明可见的自由函数。函数候选显示返回类型和参数签名；输入点号触发的成员补全（`tbe.`、`in_msg.`、`someVar.`）显示已解析类型的字段和函数 |
| **由声明驱动的端口方法** | `in_port` 名称的类型为 `InPort`，`out_port` 名称的类型为 `OutPort`，而不是第二个参数指定的消息类型。成员补全、导航、参数信息、返回类型推断和成员检查由可见的 SLICC 声明驱动。`isReady` 和 `dequeue` 是普通方法/函数标识符，而非关键字或内置函数；保持作用域及显式变量遮蔽规则 |
| **参数信息** | `Ctrl+P`（或输入 `(` 自动弹出）显示函数的所有重载及参数类型，并高亮当前参数位置 |
| **参数名称行内提示** | 在已解析的自由函数和成员函数实参前显示参数名。省略未命名参数、与参数同名的实参，以及歧义重载中存在冲突的参数名 |
| **隐式类型行内提示** | 在每个有作用域的 `in_msg`、`out_msg`、`tbe`、`cache_entry` 或 `address` 绑定首次实际使用时显示推断类型，不重复标注显式变量声明 |
| **有作用域的消息变量** | 隐式 `in_msg` 仅在 `peek` 主体中可用，`out_msg` 仅在 `enqueue` 主体中可用，也包括内部嵌套块；类型来自语句的第二个参数。显式声明为 `in_msg` 或 `out_msg` 的函数参数在整个函数中优先，包括嵌套消息块；解析、补全、导航和高亮使用该参数声明的类型 |
| **引用参数** | 将 `CHIRequestMsg & out_msg` 和 `WriteMask &mask` 等参数识别为单个声明。在签名和声明/定义匹配中保留 `&` 与 `*`；成员查找和支持的调用结果兼容性检查使用底层 SLICC 类型 |
| **带注释的成员访问** | 成员补全与导航支持 `.` 两侧的空白和注释，也支持链式访问 |
| **类型感知的成员错误检查** | 根据唯一解析出的接收者所对应的可见 SLICC 声明及完整 `interface` 层次（包括外部声明），检查 `.` 后未知的字段和方法名。遵循词法遮蔽、消息作用域、引用/指针参数和 machine/include 归属；避免在未解析的访问链上产生级联错误 |
| **函数结果类型检查** | 检查赋给已声明变量或字段的函数结果（包括初始化），以及作为实参传递的函数结果。推断支持的简单表达式、字段访问、嵌套调用和括号表达式；仅报告可见 SLICC 类型中能够确定的不匹配 |
| **未解析标识符错误** | 无法解析到任何声明的标识符以红色下划线标出 |
| **结构视图** | `Alt+7` 显示 machine → in_ports、out_ports、actions、transitions、functions 的树形结构 |
| **代码折叠** | 支持折叠 `machine`、`in_port`、`action` 和独立函数的主体 |
| **括号匹配** | 自动配对并高亮匹配的 `{ }`、`( )`、`[ ]` |
| **注释切换** | `Ctrl+/` 切换行注释，`Ctrl+Shift+/` 切换块注释 |
| **颜色自定义** | Settings → Editor → Color Scheme → **SLICC (gem5 Ruby)** |
| **跨文件解析** | 标识符解析覆盖同一协议目录内的文件，以及 `RubySlicc_interfaces.slicc` 中的全局声明 |

### 外部类型导航

请保留 Ruby 源码树中的 `Kconfig`、`SConscript`、协议声明和 C++ 源码；
需要 Ruby 目录之外的头文件时，建议打开完整的 gem5 源码仓库。
在 `RubySlicc_Types.sm` 中的 `MessageBuffer` 或 `CacheMemory` 等外部声明上，
使用 `Ctrl+B` 即可打开 C++ 定义。
普通 SLICC 类型的使用位置仍解析到其 SLICC 声明。

对于 machine 及其包含的声明，非限定的外部类型名称优先使用所属协议的命名空间，
然后使用共享的 `gem5::ruby` 命名空间。例如，CHI 的 `MN_TBEStorage` 会解析到
`gem5::ruby::CHI::MN_TBEStorage`，而不是其他协议中的同名类型。
协议归属通过 `.slicc` 声明和字面量 include 关系确定，并使用 Kconfig 中的
`RUBY_PROTOCOL_<name>` 条目及可选的 `bool` 提示文本别名；提示文本中的空白会归一为下划线。
协议归属未知或存在歧义时，不会搜索其他命名空间。
只有写在 machine 内部的 include 才继承该 machine 的归属；
即使位于同一文件中，machine 外部的全局声明和 include 也仍优先查找共享 Ruby 命名空间。
如果 machine 的协议归属未知或存在歧义，且共享命名空间中也没有匹配，
则以警告说明上下文不可用，而不是断言 C++ 定义必然不存在。

查找使用基于源码的保守静态关系图，支持字面量 `Source` / `MakeInclude` / include 路径，
并合并考虑条件分支。它不会执行 Kconfig/SCons，也不会对 C++ 进行预处理；
计算生成的路径和宏生成的定义可能无法解析。目前不支持 C++ 成员/调用导航。
已检查的文件树、命名空间/别名规则、豁免情况和诊断边界见
[RUBY_CPP_INTEGRATION.md](RUBY_CPP_INTEGRATION.md)。

### 行内提示

在 1.5.0 中，可以在 **Settings → Editor → Inlay Hints → SLICC** 下
分别启用或禁用参数名称提示与隐式类型提示。

参数名称遵循解析后的函数签名及 machine/include/member 作用域。
重载解析存在歧义时，只有各候选参数名一致的位置才显示提示。
未命名参数，以及名称已经与参数相同的实参，不显示提示。

类型提示仅覆盖隐式 SLICC 变量：如果没有匹配的显式函数参数，
`in_msg` 和 `out_msg` 分别使用最近一层所属 `peek` 或 `enqueue` 的第二个参数类型；
`tbe`、`cache_entry` 和 `address` 仅限 action 内使用。
每个绑定只在首次实际使用时标注；显式可见变量（包括 `in_msg` / `out_msg` 参数）不会重复标注。
提示计算会等待索引完成。

### 函数参数

插件识别 `WriteMask &mask`、`CHIRequestMsg & out_msg` 等引用参数，
也识别 `Packet *pkt` 等指针参数。
引用与指针修饰保留在显示的签名和声明/定义匹配中。
成员查找及支持的调用结果兼容性检查使用底层 SLICC 类型，而非 C++ 限定符转换规则。

名为 `in_msg` 或 `out_msg` 的函数参数，在整个函数中优先于隐式消息绑定，
也包括嵌套的 `peek` / `enqueue` 主体。
这些名称的使用位置会解析并跳转到参数，使用普通参数的高亮与补全，
不会重复显示隐式类型行内提示。
没有此类参数时，仍遵循原有的消息主体作用域限制，并使用最近匹配语句第二个参数的类型。

### 成员错误检查

当接收者类型及其完整 `interface` 层次可以唯一解析时，
插件会对 `.` 后未知的字段或方法名报错。
检查范围是可见 SLICC 声明公开的成员，包括标记为 `external="yes"` 的结构体和
`external_type`，而不是搜索 C++ 类中额外的未声明成员。
已声明的成员按名称接受；这不是完整的参数兼容性或 C++ 类型检查。

简单接收者和字段访问链遵循词法遮蔽、machine/include 归属、有作用域的消息类型，
以及引用/指针参数的底层类型。
接收者未知、接口缺失或成环、同一作用域内的类型有歧义、声明格式有误，或正在索引时，
会推迟诊断而不是猜测。
此检查不会推断调用结果、括号表达式或索引表达式等接收者的类型。
访问链中的一个元素未能解析时，不会对其下游成员继续产生额外错误。

### 函数结果类型检查

插件会将函数结果与赋值目标的类型（包括变量初始化），
或另一个调用中接收该结果的参数类型进行比较。
例如，如果 `getCount()` 返回 `int`，则 `bool ready := getCount()`
会在结果表达式上报告不匹配。
`consumeCount(getMessage())` 等调用会根据可见的参数类型进行检查。
将 `void` 结果作为值使用也会报错。

推断覆盖简单变量、字段、自由函数/成员函数调用、嵌套调用和括号表达式，
并遵循词法、消息、machine/include 和类型归属。
候选选择依据实参类型，而不是赋值目标期望的类型。
兼容性遵循支持的 SLICC 名义类型和 `interface` 关系；引用/指针调用限定符不代表
C++ 转换检查。未知表达式、有歧义的类型声明、冲突的重载、不完整声明、复杂运算符、
索引表达式和索引过程中会推迟诊断。
如果多个重载对推断出的返回类型一致，仍可得到确定的结果类型。
此功能不会验证每个原始实参、函数体的 `return` 语句或完整的 C++ 隐式转换。
原有的成员名称检查保持独立。

## 支持的保留字

1.5.0 对 gem5 SLICC 保留字列表中的全部 30 个词提供高亮和补全。
关键字区分大小写，包括必须大写的 `OOD`。

```text
protocol include global machine in_port out_port action transition
structure external_type enumeration state_declaration
peek stall_and_wait wakeup_port enqueue check_allocate check_next_cycle
check_stop_slots check_on_cache_probe static_cast
if is_valid is_invalid else return void new OOD defer_enqueueing
```

除 `interface` 和 `desc` 外，`in_trans`、`out_trans`、`main`、`block_on`、`format`、`wake_up`、
`rsc_stall_handler`、`prot_stall_handler`、`return_by_ref` 和 `return_by_pointer` 也被识别为属性关键字。
`DPRINTF`、`DPRINTFN`、`APPEND_TRANSITION_COMMENT` 和 `stallPort` 被识别为内置调用，
不计入上述 30 个保留字。原有的其他 SLICC 关键字和内置函数仍受支持。
`isReady` 和 `dequeue` 是通过可见方法或函数声明解析的普通标识符，
而不是特殊关键字或内置调用。

### Debug flag 与格式字符串

在 `DPRINTF(RubySlicc, "address=%#x count=%d", address, count)` 中，
第一个参数是 debug flag，随后依次是格式字符串和各个值。
补全从 gem5 源码仓库或当前项目中所有 `SConscript` 文件的字面量 `DebugFlag` / `CompoundFlag`
声明中读取候选，也包括 `src/mem/SConscript` 中的 `HtmMem` 等非 Ruby flag。
缺少 flag 会报错；源码上下文不可用或不完整时，会推迟未知 flag 的错误检查。
`DPRINTFN("count=%d", count)` 没有 flag 参数。

`DPRINTF` / `DPRINTFN` 的字面量格式参数数量不匹配会产生警告，而不是错误；
flag 诊断和缺少格式字符串仍为错误。
这些检查只比较参数数量，不比较类型。
`%%` 不消耗参数；`%*.*f` 中的宽度、精度和值各消耗一个参数。
嵌套函数中的逗号不会拆分外层实参。
动态或不支持的格式以及不完整调用会推迟数量检查；这些检查不会执行 SCons 或预处理 C++。

### Transition、函数与说明辅助

Transition 检查解析 action 名称，并展开 state/event 集合，
按 machine/include 顺序发现后出现的重复组合。
State/event 列表接受空白、逗号和分号分隔；第三个 next-state 参数可以是 `*`，
且不会禁用这些检查。
缺失函数诊断检查可见自由函数和支持的限定作用域名称，而非完整的重载参数兼容性。
点号成员名称诊断仍保持独立。
作用域不明确或不完整、接收者未知及索引过程中，会推迟相应检查而不是猜测。

在声明或已解析的使用位置悬停，或按 `Ctrl+Q`，可显示其自身的 `desc`。
支持结构体、字段、枚举/常量、函数和 action。
说明会保留换行并转义 HTML；签名相同的函数原型和定义可以互相提供说明。
没有说明的符号不会继承外层声明的文本。

文件分析复用协议/include 图、文件级符号索引和注解会话中的 C++ 查找上下文，
避免反复扫描项目和 PSI。
相关源码编辑、项目根目录变化和索引状态变化会使相应缓存失效。
这些功能用于编辑器辅助，不能替代 SLICC 编译器的语法分析或诊断。

## 尚未实现

- **重命名重构**（`Shift+F6`）— `SliccNamedElement.setName()` 尚未实现；重命名符号不会更新所有使用位置。
- **代码格式化** — 尚无 `FormattingModelBuilder`，不支持自动缩进和重新格式化（`Ctrl+Alt+L`）。
- **实时模板** — 尚未内置常见 SLICC 模式的代码片段。

---

## 构建

### 前置条件

- JDK 21+
- `gradle/wrapper/gradle-wrapper.jar`（未提交到仓库，参见 [`GET_WRAPPER_JAR.md`](GET_WRAPPER_JAR.md)）
- 首次构建需要联网（Gradle 会自动下载 IntelliJ SDK）

### 可选：使用本地 IDE 安装

默认会自动下载 CLion 2026.1。若想使用本机已安装的 IDE 以加快构建，
请在项目根目录的 `gradle.properties` 中添加以下配置
（该文件已被 gitignore 忽略，请勿提交）：

```properties
ide.localPath=/path/to/CLion
# Windows 示例：ide.localPath=C:/Program Files/JetBrains/CLion 2026.1
# macOS 示例：  ide.localPath=/Applications/CLion.app/Contents
```

### Windows：后端以代码 77 退出

如果 `runIde` 报告 Rider host 以代码 77 退出，请检查
`.intellijPlatform/sandbox/slicc-plugin/<IDE-version>/log/` 下的
`backend.<timestamp>.<pid>.log`，查看根本原因。
CLion 也使用该后端，因此运行 CLion 时也可能出现此消息。

如果日志显示加载 DLL 时出现 `COR_E_PATHTOOLONG`、`0x800700CE`
或 "The filename or extension is too long"，请使用前文的 `ide.localPath`
选择安装在较短路径中的 IDE。
IDE 解压在 Gradle 缓存的深层路径中时，可能超出 Windows 路径长度限制，
尤其是 `GRADLE_USER_HOME` 较长（例如使用 Scoop 安装目录）的情况。
也可以在启动 Gradle 前改用较短的 Gradle 用户目录；这会建立独立缓存，可能需要重新下载依赖。
还可以通过较短路径的 Windows 目录联接访问现有缓存中的 IDE，并让 `ide.localPath` 指向该联接。
请保持联接目标完整；清理对应 Gradle 缓存后，需要重新创建联接。

单独出现 `Debug privileges not enabled` 并不能确定后端退出的原因。
请重新启动 `runIde`，并查看新生成的后端日志来验证修复。
本地路径应保存在被 gitignore 忽略的 `gradle.properties` 文件中；
如果本机 IDE 安装版本更新，还需单独检查插件与所支持的最旧 IDE 版本之间的兼容性。

如果 Gradle 找不到该 IDE 所需的 Java 工具链，
可以将 `gradle.properties` 中的 `org.gradle.java.installations.paths`
指向 IDE 附带的 `jbr` 目录（前提是其中包含 `bin/javac.exe`），或者安装匹配的 JDK。

### 构建命令

```bash
# 构建可分发的插件 ZIP
./gradlew buildPlugin
# 输出：build/distributions/slicc-plugin-1.6.0.zip

# 启动加载了插件的沙盒 IDE（主要测试方式）
./gradlew runIde
```

---

## 在 CLion / IntelliJ IDEA 中安装

1. 打开 **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. 选择 `build/distributions/slicc-plugin-1.6.0.zip`
3. 重新启动 IDE
4. 打开任意 `.sm` 或 `.slicc` 文件，高亮会立即启用

---

## 项目结构

```
slicc-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── java/com/slicc/plugin/
    │   ├── lang/
    │   │   ├── SliccLanguage.java          # 语言单例
    │   │   ├── SliccFileType.java          # .sm / .slicc 文件类型
    │   │   ├── SliccCommenter.java         # // 与 /* */ 注释支持
    │   │   └── SliccBraceMatcher.java      # 括号自动配对
    │   ├── lexer/
    │   │   ├── SliccLexer.java             # 手写词法分析器
    │   │   ├── SliccTokenType.java         # IElementType 包装类
    │   │   └── SliccTokenTypes.java        # 所有 token 类型常量
    │   ├── parser/
    │   │   ├── SliccParserDefinition.java  # 解析器与注释/空白集合
    │   │   ├── SliccParser.java            # 递归下降解析器
    │   │   └── SliccPsiElementFactory.java # PSI 节点工厂
    │   ├── psi/
    │   │   ├── SliccFile.java              # 根 PSI 文件节点
    │   │   ├── SliccCompositeElement.java  # 复合 PSI 节点基类
    │   │   ├── SliccTypes.java             # 所有复合节点类型常量
    │   │   ├── SliccNamedElement.java      # 所有具名声明的基类
    │   │   ├── SliccMachineDecl.java
    │   │   ├── SliccStructureDecl.java
    │   │   ├── SliccEnumerationDecl.java
    │   │   ├── SliccStateDecl.java
    │   │   ├── SliccExternalTypeDecl.java
    │   │   ├── SliccActionDecl.java
    │   │   ├── SliccTransitionDecl.java
    │   │   ├── SliccFunctionDecl.java
    │   │   ├── SliccInPortDecl.java
    │   │   ├── SliccOutPortDecl.java
    │   │   ├── SliccFieldDecl.java
    │   │   ├── SliccEnumMember.java
    │   │   └── SliccIncludeDecl.java
    │   ├── highlighting/
    │   │   ├── SliccSyntaxHighlighter.java
    │   │   ├── SliccSyntaxHighlighterFactory.java
    │   │   ├── SliccColorSettingsPage.java
    │   │   ├── SliccAnnotator.java         # 语义高亮与未解析标识符错误
    │   │   └── SliccFoldingBuilder.java
    │   ├── completion/
    │   │   ├── SliccCompletionContributor.java  # 关键字、符号和点号成员补全
    │   │   ├── SliccParameterInfoHandler.java   # Ctrl+P 参数信息弹窗
    │   │   └── SliccTypedHandler.java           # 输入 '(' 自动弹出
    │   ├── inlay/
    │   │   ├── SliccInlayHints.java              # 作用域感知的参数/类型提示模型
    │   │   └── SliccInlayPresentation.java       # 声明式提示渲染
    │   ├── navigation/
    │   │   ├── SliccReference.java         # 标识符 → 声明解析
    │   │   ├── SliccReferenceContributor.java
    │   │   ├── SliccFindUsagesProvider.java
    │   │   ├── SliccGotoDeclarationHandler.java
    │   │   ├── SliccGotoSymbolContributor.java
    │   │   ├── SliccProtocolIndex.java
    │   │   └── SliccStructureViewFactory.java
    │   └── icons/
    │       └── SliccIcons.java
    └── resources/
        ├── META-INF/plugin.xml
        └── icons/
```

---

## 参与贡献

欢迎提交问题报告、功能建议和 pull request。

- **报告问题或建议功能**：[提交 issue](../../issues)
- **已知限制**：参见上方“尚未实现”章节

报告问题时，请提供：
- IDE 版本（CLion / IntelliJ IDEA）
- 能复现问题的最小 `.sm` 或 `.slicc` 代码片段
- 预期行为与实际行为的差异

---

## 致谢

感谢 **Codex** 对本插件开发工作的帮助。
使用的模型配置为 **GPT-6.1 Sol（Ultra）**。

---

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
