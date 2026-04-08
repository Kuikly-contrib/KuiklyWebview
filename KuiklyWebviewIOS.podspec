Pod::Spec.new do |s|
  s.name             = 'KuiklyWebviewIOS'
  s.version          = '1.0.0'
  s.summary          = 'KuiklyWebView iOS native implementation'
  s.description      = <<-DESC
    iOS native WebView component for Kuikly framework.
    Based on WKWebView, supports JSBridge protocol.
  DESC
  s.homepage         = 'https://github.com/Kuikly-contrib/KuiklyWebview'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'aspect' => 'aspect@example.com' }
  s.source           = { :git => 'https://github.com/Kuikly-contrib/KuiklyWebview.git', :tag => s.version.to_s }

  s.ios.deployment_target = '14.1'
  s.source_files     = 'KuiklyWebviewIOS/Classes/**/*.{h,m}'
  s.frameworks       = 'WebKit', 'UIKit'

  s.dependency 'OpenKuiklyIOSRender', '~> 2.7.0'
end
